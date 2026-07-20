package com.kavach.app.data.remote.websocket

import android.util.Log
import com.kavach.app.BuildConfig
import com.kavach.app.data.local.SessionManager
import com.kavach.app.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time WebSocket client for the guardian alert stream.
 *
 * Connects to: ws(s)://host/ws/guardian/{guardian_id}?token=<JWT>
 *
 * Reconnect strategy: exponential back-off from 1 s up to 30 s.
 * Auth failure (close 1008) clears the session and stops reconnecting.
 */
@Singleton
class GuardianWebSocketClient @Inject constructor(
    private val sessionManager: SessionManager,
) {
    companion object {
        private const val TAG              = "GuardianWS"
        private const val NORMAL_CLOSE     = 1000
        private const val POLICY_VIOLATION = 1008
        private const val PING_FRAME       = """{"type":"ping"}"""
    }

    // Raw JSON frames (minus heartbeat pings)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _state = MutableStateFlow<WsState>(WsState.Disconnected)
    val state: StateFlow<WsState> = _state.asStateFlow()

    private var ws: WebSocket? = null
    private var connectScope: CoroutineScope? = null
    private val shouldReconnect = AtomicBoolean(false)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)        // long-lived connection
        .pingInterval(25, TimeUnit.SECONDS)      // OkHttp keep-alive
        .build()

    /** Begin connecting (or reconnecting). Safe to call multiple times. */
    fun connect(guardianId: String) {
        connectScope?.cancel()
        connectScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        shouldReconnect.set(true)

        connectScope!!.launch {
            var backoffMs = Constants.WS_RECONNECT_DELAY_MS

            while (shouldReconnect.get()) {
                val token = sessionManager.getJwtTokenOnce()
                if (token.isNullOrBlank()) {
                    _state.value = WsState.AuthError
                    return@launch
                }

                _state.value = WsState.Connecting
                val url = "${BuildConfig.WS_BASE_URL}ws/guardian/$guardianId?token=$token"
                val request = Request.Builder().url(url).build()

                // Latch for this connection attempt
                var closed = false

                ws = httpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(socket: WebSocket, response: Response) {
                        backoffMs = Constants.WS_RECONNECT_DELAY_MS
                        _state.value = WsState.Connected
                        Log.d(TAG, "Connected → guardian $guardianId")
                    }

                    override fun onMessage(socket: WebSocket, text: String) {
                        if (text != PING_FRAME) {
                            connectScope?.launch { _messages.emit(text) }
                        }
                    }

                    override fun onMessage(socket: WebSocket, bytes: ByteString) =
                        onMessage(socket, bytes.utf8())

                    override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                        socket.close(NORMAL_CLOSE, null)
                    }

                    override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "Closed code=$code reason=$reason")
                        closed = true
                        if (code == POLICY_VIOLATION) {
                            shouldReconnect.set(false)
                            connectScope?.launch {
                                sessionManager.clearSession()
                                _state.value = WsState.AuthError
                            }
                        } else {
                            _state.value = WsState.Disconnected
                        }
                    }

                    override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "Failure: ${t.message}")
                        closed = true
                        _state.value = WsState.Error(t.message ?: "Unknown error")
                    }
                })

                // Poll until this socket closes
                while (!closed && shouldReconnect.get()) {
                    delay(500)
                }

                if (shouldReconnect.get() && _state.value !is WsState.AuthError) {
                    _state.value = WsState.Reconnecting
                    Log.d(TAG, "Reconnecting in ${backoffMs}ms")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(Constants.WS_MAX_RECONNECT_DELAY_MS)
                }
            }
        }
    }

    /** Gracefully disconnect and stop all reconnect attempts. */
    fun disconnect() {
        shouldReconnect.set(false)
        ws?.close(NORMAL_CLOSE, "Client disconnect")
        ws = null
        connectScope?.cancel()
        connectScope = null
        _state.value = WsState.Disconnected
    }
}

sealed class WsState {
    data object Connecting   : WsState()
    data object Connected    : WsState()
    data object Disconnected : WsState()
    data object Reconnecting : WsState()
    data object AuthError    : WsState()
    data class  Error(val message: String) : WsState()
}
