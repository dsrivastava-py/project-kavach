package com.kavach.app.presentation.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kavach.app.presentation.theme.RiskHigh
import com.kavach.app.presentation.theme.RiskLow
import com.kavach.app.presentation.theme.RiskMedium

// ── Primary Button ─────────────────────────────────────────────────────────

/**
 * Primary action button. Shows a spinner when [loading] is true.
 */
@Composable
fun KavachButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick   = onClick,
        modifier  = modifier.height(52.dp),
        enabled   = enabled && !loading,
        shape     = RoundedCornerShape(12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier    = Modifier.size(20.dp),
                color       = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ── Outlined Button ────────────────────────────────────────────────────────

@Composable
fun KavachOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier.height(52.dp),
        enabled  = enabled && !loading,
        shape    = RoundedCornerShape(12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ── TextField ─────────────────────────────────────────────────────────────

@Composable
fun KavachTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value                = value,
            onValueChange        = onValueChange,
            label                = { Text(label) },
            placeholder          = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
            leadingIcon          = leadingIcon?.let { { Icon(it, contentDescription = null) } },
            trailingIcon         = trailingIcon,
            isError              = isError,
            singleLine           = singleLine,
            keyboardOptions      = keyboardOptions,
            keyboardActions      = keyboardActions,
            visualTransformation = visualTransformation,
            shape                = RoundedCornerShape(12.dp),
            modifier             = Modifier.fillMaxWidth(),
        )
        if (isError && errorMessage != null) {
            Text(
                text     = errorMessage,
                color    = MaterialTheme.colorScheme.error,
                style    = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

// ── Loading ────────────────────────────────────────────────────────────────

/**
 * Full-area loading indicator. Apply [modifier] to constrain size (e.g. `Modifier.height(120.dp)`).
 */
@Composable
fun KavachLoadingView(
    modifier: Modifier = Modifier.fillMaxSize(),
    message: String = "Loading…",
) {
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            text  = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Error ──────────────────────────────────────────────────────────────────

/**
 * Full-area error state. [onRetry] is optional — omit for non-retryable errors.
 */
@Composable
fun KavachErrorView(
    message: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier            = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector        = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.error,
            modifier           = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurface,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            KavachButton(
                text     = "Retry",
                onClick  = onRetry,
                modifier = Modifier.widthIn(min = 120.dp),
            )
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────

@Composable
fun KavachEmptyView(
    message: String = "Nothing here yet",
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Column(
        modifier            = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector        = Icons.Default.Inbox,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Top App Bar ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KavachTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        colors  = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// ── Risk Badge ─────────────────────────────────────────────────────────────

@Composable
fun RiskBadge(score: Double, modifier: Modifier = Modifier) {
    val (color, label) = when {
        score >= 0.7 -> RiskHigh   to "High"
        score >= 0.4 -> RiskMedium to "Medium"
        else         -> RiskLow    to "Low"
    }
    Surface(
        modifier = modifier,
        shape    = CircleShape,
        color    = color.copy(alpha = 0.15f),
    ) {
        Text(
            text     = "$label ${(score * 100).toInt()}%",
            color    = color,
            style    = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ── Incident Card ──────────────────────────────────────────────────────────

@Composable
fun IncidentCard(
    id: String,
    status: String,
    riskScore: Double,
    startedAt: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "Incident #${id.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = status.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = startedAt.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            RiskBadge(score = riskScore)
        }
    }
}

// ── Pulsing live indicator ─────────────────────────────────────────────────

@Composable
fun PulsingDot(color: Color = Color(0xFF4CAF50), modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label         = "alpha",
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

// ── Section Header ─────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp),
    )
}
