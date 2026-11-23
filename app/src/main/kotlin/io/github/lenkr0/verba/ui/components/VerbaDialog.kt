package io.github.lenkr0.verba.ui.components

import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import android.view.WindowManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import io.github.lenkr0.verba.ui.theme.LocalEInkMode

/**
 * AlertDialog that follows E-ink mode: instead of dimming the screen behind it
 * (a gray that e-ink can only dither), it keeps the background as it is and
 * draws a solid border around the dialog.
 */
@Composable
fun VerbaAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    val eInk = LocalEInkMode.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = {
            if (eInk) EInkDialogWindow()
            text()
        },
        modifier = if (eInk) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.outline, AlertDialogDefaults.shape)
        } else {
            Modifier
        },
        tonalElevation = if (eInk) 0.dp else AlertDialogDefaults.TonalElevation,
    )
}

/**
 * No dimming behind the dialog. Clearing the flag (not just a zero dim amount)
 * matters: a dimming window takes over the system bar icon colors, which
 * would turn white on the white e-ink background.
 */
@Composable
private fun EInkDialogWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
}
