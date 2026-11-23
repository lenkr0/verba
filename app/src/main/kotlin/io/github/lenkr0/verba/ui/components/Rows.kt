package io.github.lenkr0.verba.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import io.github.lenkr0.verba.R

// Rows use the plain ListItem plus explicit selectable/toggleable modifiers, so
// TalkBack announces them as radio button or switch. They are transparent so
// they blend into dialogs as well as into screens.

@Composable
private fun rowColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = Color.Unspecified,
)

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        colors = rowColors(),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
    ) { Text(label) }
}

@Composable
fun SwitchRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        colors = rowColors(),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) { Text(title) }
}

@Composable
fun ClickRow(title: String, summary: String, onClick: () -> Unit, external: Boolean = false) {
    ListItem(
        colors = rowColors(),
        modifier = Modifier.clickable(onClick = onClick),
        supportingContent = { Text(summary) },
        trailingContent = {
            val icon = if (external) R.drawable.ic_open_in_new else R.drawable.ic_chevron_right
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) { Text(title) }
}

@Composable
fun InfoRow(title: String, summary: String) {
    ListItem(colors = rowColors(), supportingContent = { Text(summary) }) { Text(title) }
}
