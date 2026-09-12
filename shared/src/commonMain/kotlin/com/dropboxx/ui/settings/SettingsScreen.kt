package com.dropboxx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropboxx.core.AppInfo
import com.dropboxx.core.shortFingerprint
import com.dropboxx.domain.BoxAccess
import com.dropboxx.domain.ThemeMode
import com.dropboxx.ui.components.SectionHeader
import com.dropboxx.ui.theme.AppIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val trusted by viewModel.trusted.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()

    var alias by remember { mutableStateOf(s.alias) }
    var pin by remember { mutableStateOf(s.pin) }
    var port by remember { mutableStateOf(s.port.toString()) }
    LaunchedEffect(s.alias) { alias = s.alias }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { SectionHeader("This device") }
        item {
            OutlinedTextField(alias, { alias = it }, label = { Text("Device name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                trailingIcon = { if (alias != s.alias) TextButton(onClick = { viewModel.setAlias(alias) }) { Text("Save") } })
        }
        item {
            ListItem(headlineContent = { Text("Fingerprint") }, supportingContent = { Text(shortFingerprint(me.fingerprint)) }, leadingContent = { Icon(AppIcons.Shield, contentDescription = null) })
        }

        item { SectionHeader("My box") }
        item {
            Text("Who can open your box", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.boxAccess == BoxAccess.ASK, onClick = { viewModel.setBoxAccess(BoxAccess.ASK) }, label = { Text("Ask me") })
                FilterChip(selected = s.boxAccess == BoxAccess.TRUSTED_ONLY, onClick = { viewModel.setBoxAccess(BoxAccess.TRUSTED_ONLY) }, label = { Text("Trusted only") })
                FilterChip(selected = s.boxAccess == BoxAccess.EVERYONE, onClick = { viewModel.setBoxAccess(BoxAccess.EVERYONE) }, label = { Text("Anyone nearby") })
            }
        }

        item { SectionHeader("Receiving") }
        item {
            OutlinedTextField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, label = { Text("Require PIN (empty = off)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                trailingIcon = { if (pin != s.pin) TextButton(onClick = { viewModel.setPin(pin) }) { Text("Save") } })
        }
        item { SwitchRow("Copy received text automatically", "Text and links go straight to your clipboard", s.autoCopyText, viewModel::setAutoCopyText) }
        item { SwitchRow("Open received links automatically", "Links open in your browser as they arrive", s.autoOpenLinks, viewModel::setAutoOpenLinks) }
        if (viewModel.canPickDirectory) item {
            ListItem(headlineContent = { Text("Save folder") }, supportingContent = { Text(s.saveDirectory) }, leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
                trailingContent = { TextButton(onClick = viewModel::pickSaveDirectory) { Text("Change") } })
        }

        item { SectionHeader("Appearance") }
        item {
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(selected = s.themeMode == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }
        if (viewModel.supportsTray) {
            item { SwitchRow("Keep running in the tray", "Closing the window keeps DropBoxx receiving in the background", s.minimizeToTray, viewModel::setMinimizeToTray) }
            item { SwitchRow("Start with Windows", "Launch DropBoxx when you sign in", s.launchAtStartup, viewModel::setLaunchAtStartup) }
        }

        item { SectionHeader("Trusted devices") }
        if (trusted.isEmpty()) item { Text("None yet. Tick \"Always accept\" when accepting a transfer.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
        items(trusted, key = { it.id }) { d ->
            ListItem(headlineContent = { Text(d.alias) }, supportingContent = { Text(shortFingerprint(d.fingerprint)) },
                trailingContent = { TextButton(onClick = { viewModel.revoke(d.id) }) { Text("Remove") } })
        }

        item { SectionHeader("Advanced") }
        item {
            OutlinedTextField(port, { port = it.filter { c -> c.isDigit() }.take(5) }, label = { Text("Port (default ${AppInfo.DEFAULT_PORT})") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                trailingIcon = { if (port != s.port.toString()) TextButton(onClick = { port.toIntOrNull()?.let(viewModel::setPort) }) { Text("Apply") } })
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { Text("${AppInfo.NAME} ${AppInfo.VERSION} - protocol v${AppInfo.PROTOCOL_VERSION}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp)) }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, trailingContent = { Switch(checked, onChange) })
}
