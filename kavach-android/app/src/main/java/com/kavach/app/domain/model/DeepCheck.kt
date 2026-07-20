package com.kavach.app.domain.model

/**
 * Domain model for a deep-check session.
 *
 * HARD RULE: [spoofScore] must ALWAYS be shown with [spoofDisclaimer].
 * Never display the score as a definitive verdict.
 */
data class DeepCheckSession(
    val sessionId: String,
    val status: DeepCheckStatus,
    val transcript: String? = null,
    val redFlags: List<String> = emptyList(),
    val spoofScore: Double? = null,
    val assistiveOnly: Boolean = true,
    val spoofDisclaimer: String? = null,
    val summary: String? = null,
    val confidence: Double? = null,
)

enum class DeepCheckStatus(val value: String) {
    PENDING("pending"),
    PROCESSING("processing"),
    DONE("done"),
    ERROR("error");

    val isTerminal: Boolean get() = this in listOf(DONE, ERROR)

    companion object {
        fun fromString(value: String): DeepCheckStatus =
            entries.firstOrNull { it.value == value } ?: PENDING
    }
}
