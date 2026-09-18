package com.dropnest.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropnest.core.AppInfo
import com.dropnest.core.shortFingerprint
import com.dropnest.domain.BoxAccess
import com.dropnest.domain.ThemeMode
import com.dropnest.ui.components.HSpace
import com.dropnest.ui.components.Heading
import com.dropnest.ui.components.Kicker
import com.dropnest.ui.components.Muted
import com.dropnest.ui.components.NButton
import com.dropnest.ui.components.NButtonStyle
import com.dropnest.ui.components.NCard
import com.dropnest.ui.components.NField
import com.dropnest.ui.components.NSeg
import com.dropnest.ui.components.NSwitch
import com.dropnest.ui.components.VSpace
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(wide: Boolean, onReplayTour: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val trusted by viewModel.trusted.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    var alias by remember { mutableStateOf(s.alias) }
    var pin by remember { mutableStateOf(s.pin) }
    var port by remember { mutableStateOf(s.port.toString()) }
    LaunchedEffect(s.alias) { alias = s.alias }
    val t = N

    val cards: List<@Composable () -> Unit> = listOf(
        {
            SettingsCard("Identity") {
                NField(alias, { alias = it }, label = "Device name", trailing = { if (alias != s.alias) NButton("Save", { viewModel.setAlias(alias) }, style = NButtonStyle.Ghost, fontSize = 11, padding = PaddingValues(6.dp, 4.dp)) })
                Muted("Fingerprint ${shortFingerprint(me.fingerprint)}", 11)
            }
        },
        {
            SettingsCard("Appearance") {
                Text("Theme", color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                NSeg(listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light"), s.themeMode, viewModel::setTheme, fill = !wide)
                SwitchRow("Motion & depth", "3D nest, orbit and card flight", s.motion, viewModel::setMotion)
            }
        },
        {
            SettingsCard("My box") {
                Text("Who can open my box", color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                NSeg(listOf(BoxAccess.ASK to "Ask me", BoxAccess.TRUSTED_ONLY to "Trusted only", BoxAccess.EVERYONE to if (wide) "Anyone nearby" else "Anyone"), s.boxAccess, viewModel::setBoxAccess, fill = !wide)
                NField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, label = "Require PIN (empty = off)", numeric = true, password = true,
                    trailing = { if (pin != s.pin) NButton("Save", { viewModel.setPin(pin) }, style = NButtonStyle.Ghost, fontSize = 11, padding = PaddingValues(6.dp, 4.dp)) })
            }
        },
        {
            SettingsCard("Receiving") {
                SwitchRow("Copy received text automatically", "Text and links go straight to your clipboard", s.autoCopyText, viewModel::setAutoCopyText)
                SwitchRow("Open received links automatically", "Links open in your browser as they arrive", s.autoOpenLinks, viewModel::setAutoOpenLinks)
                if (viewModel.canPickDirectory) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Save received files to", color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium); Muted(s.saveDirectory, 11, maxLines = 1) }
                        NButton("Change", viewModel::pickSaveDirectory, fontSize = 11)
                    }
                }
                if (viewModel.supportsTray) {
                    SwitchRow("Keep running in the tray", "Closing the window keeps DropNest receiving", s.minimizeToTray, viewModel::setMinimizeToTray)
                    SwitchRow("Start with Windows", "Launch DropNest when you sign in", s.launchAtStartup, viewModel::setLaunchAtStartup)
                }
            }
        },
        {
            SettingsCard("Trusted devices") {
                if (trusted.isEmpty()) Muted("None yet. Choose \"Always allow\" when a device asks to open your box.", 11)
                trusted.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ph.ShieldCheck, null, tint = t.accent, modifier = Modifier.size(15.dp)); HSpace(8.dp)
                        Column(Modifier.weight(1f)) { Text(d.alias, color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium); Muted(shortFingerprint(d.fingerprint), 10) }
                        NButton("Remove", { viewModel.revoke(d.id) }, style = NButtonStyle.Ghost, fontSize = 11)
                    }
                }
            }
        },
        {
            SettingsCard("Advanced") {
                NField(port, { port = it.filter { c -> c.isDigit() }.take(5) }, label = "Port (default ${AppInfo.DEFAULT_PORT})", numeric = true,
                    trailing = { if (port != s.port.toString()) NButton("Apply", { port.toIntOrNull()?.let(viewModel::setPort) }, style = NButtonStyle.Ghost, fontSize = 11, padding = PaddingValues(6.dp, 4.dp)) })
                NButton("Replay first-run tour", { viewModel.replayOnboarding(); onReplayTour() }, Modifier.fillMaxWidth())
                Muted("${AppInfo.NAME} ${AppInfo.VERSION} · protocol v${AppInfo.PROTOCOL_VERSION}", 10)
            }
        },
    )

    Column(Modifier.fillMaxSize().padding(if (wide) 24.dp else 14.dp, if (wide) 20.dp else 8.dp, if (wide) 24.dp else 14.dp, 0.dp)) {
        Kicker("Preferences"); VSpace(if (wide) 5.dp else 3.dp); Heading("Settings", if (wide) 26 else 24)
        VSpace(if (wide) 16.dp else 12.dp)
        LazyVerticalGrid(
            GridCells.Adaptive(if (wide) 300.dp else 600.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(if (wide) 14.dp else 10.dp),
        ) { items(cards.size) { i -> cards[i]() } }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    NCard(Modifier.fillMaxWidth(), radius = 14.dp, padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker(title, accent = false, size = 11)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = N
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Muted(subtitle, 11)
        }
        Spacer(Modifier.size(11.dp))
        NSwitch(checked, onChange)
    }
}
