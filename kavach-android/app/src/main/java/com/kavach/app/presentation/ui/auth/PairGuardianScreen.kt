package com.kavach.app.presentation.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.presentation.ui.components.*
import com.kavach.app.presentation.viewmodel.AuthUiState
import com.kavach.app.presentation.viewmodel.AuthViewModel

/**
 * Guardian pairing screen.
 * Calls POST /api/v1/guardians/pair with the 6-digit code + E.164 phone.
 */
@Composable
fun PairGuardianScreen(
    onPaired: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.pairState.collectAsStateWithLifecycle()

    var code  by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        if (state is AuthUiState.Success<*>) {
            viewModel.resetPairState()
            onPaired()
        }
    }

    Scaffold(
        topBar = { KavachTopBar(title = "Pair as Guardian", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Enter the 6-digit code from the elder's device and your phone number.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            KavachTextField(
                value         = code,
                onValueChange = { if (it.length <= 6) { code = it; codeError = null } },
                label         = "Pairing Code",
                placeholder   = "123456",
                leadingIcon   = Icons.Default.Lock,
                isError       = codeError != null,
                errorMessage  = codeError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction    = ImeAction.Next,
                ),
            )

            KavachTextField(
                value         = phone,
                onValueChange = { phone = it; phoneError = null },
                label         = "Your Phone Number (E.164)",
                placeholder   = "+919876543210",
                leadingIcon   = Icons.Default.Phone,
                isError       = phoneError != null,
                errorMessage  = phoneError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction    = ImeAction.Done,
                ),
            )

            if (state is AuthUiState.Error) {
                Text(
                    text  = (state as AuthUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.weight(1f))

            KavachButton(
                text     = "Pair",
                loading  = state is AuthUiState.Loading,
                onClick  = {
                    var valid = true
                    if (code.length != 6) { codeError = "Must be exactly 6 digits"; valid = false }
                    if (!phone.startsWith("+")) { phoneError = "Use E.164 format (+91…)"; valid = false }
                    if (valid) viewModel.pairGuardian(code, phone)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
