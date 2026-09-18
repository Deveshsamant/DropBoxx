package com.dropnest.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dropnest.model.ItemKind
import com.dropnest.ui.components.AccentTile
import com.dropnest.ui.components.HSpace
import com.dropnest.ui.components.Kicker
import com.dropnest.ui.components.Muted
import com.dropnest.ui.components.NButton
import com.dropnest.ui.components.NButtonStyle
import com.dropnest.ui.components.NCard
import com.dropnest.ui.components.NField
import com.dropnest.ui.components.NSwitch
import com.dropnest.ui.components.NestChip
import com.dropnest.ui.components.NestHero
import com.dropnest.ui.components.VSpace
import com.dropnest.ui.motion.rememberSpin
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph

private val demoChips = listOf(
    NestChip("d1", ItemKind.FILE, "image/png"), NestChip("d2", ItemKind.TEXT, "text/plain"),
    NestChip("d3", ItemKind.FILE, "application/pdf"), NestChip("d4", ItemKind.URL, "text/uri-list"),
)

/** First run: name the device, choose visibility, open the nest. */
@Composable
fun OnboardingScreen(wide: Boolean, motion: Boolean, initialName: String, visible: Boolean, onVisible: (Boolean) -> Unit, onDone: (String) -> Unit) {
    val t = N
    var name by remember { mutableStateOf(initialName) }
    val spin = rememberSpin(9.6f, motion)
    val bg = Modifier.fillMaxSize().background(t.bg).background(Brush.radialGradient(listOf(t.accentSoft, Color.Transparent), radius = 900f))

    if (wide) {
        Row(bg.padding(horizontal = 34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f).widthIn(max = 440.dp)) {
                Kicker("Welcome to DropNest"); VSpace(11.dp)
                Text("A box that\ntravels with you.", color = t.text, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.6).sp)
                VSpace(13.dp)
                Muted("Drop files, photos, text or links into your nest — with no other device around. When a peer shows up, it picks them up in one tap.", 14, Modifier.widthIn(max = 390.dp))
                VSpace(18.dp)
                Steps()
                VSpace(22.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NField(name, { name = it }, Modifier.width(190.dp))
                    HSpace(9.dp)
                    NButton("Open my nest", { onDone(name) }, style = NButtonStyle.Primary, fontSize = 13, trailing = { Icon(Ph.CaretRight, null, Modifier.size(15.dp)) })
                }
                VSpace(12.dp)
                VisibleRow(visible, onVisible)
            }
            Box(Modifier.weight(1f).fillMaxHeight(0.8f)) { NestHero(demoChips, spin, Modifier.fillMaxSize(), scale = 1.28f, motion = motion) }
        }
    } else {
        BoxWithConstraints(bg.statusBarsPadding().navigationBarsPadding()) {
        val heroHeight = (maxHeight * 0.36f).coerceIn(150.dp, 270.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(heroHeight)) { NestHero(demoChips, spin, Modifier.fillMaxSize(), scale = 0.9f, motion = motion) }
            Column(Modifier.padding(22.dp, 0.dp, 22.dp, 22.dp)) {
                Kicker("Welcome to DropNest"); VSpace(8.dp)
                Text("A box that travels with you.", color = t.text, fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp)
                VSpace(10.dp)
                Muted("Drop files, photos, text or links into your nest — even with no other device around. Your other devices pick them up in one tap.", 13)
                VSpace(22.dp)
                NField(name, { name = it }, label = "Name this phone", minHeight = 44.dp)
                VSpace(11.dp)
                VisibleRow(visible, onVisible)
                VSpace(12.dp)
                NButton("Open my nest", { onDone(name) }, Modifier.fillMaxWidth(), style = NButtonStyle.Primary, fontSize = 14, padding = PaddingValues(14.dp, 13.dp), trailing = { Icon(Ph.CaretRight, null, Modifier.size(16.dp)) })
            }
        }
        }
    }
}

@Composable
private fun Steps() {
    val t = N
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Name this device", "Stay visible on your network", "Start dropping").forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).background(t.accentSoft, androidx.compose.foundation.shape.RoundedCornerShape(7.dp)).padding(1.dp), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = t.accent, fontSize = 11.5.sp)
                }
                HSpace(10.dp)
                Text(s, color = t.text, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun VisibleRow(visible: Boolean, onVisible: (Boolean) -> Unit) {
    val t = N
    NCard(Modifier.fillMaxWidth(), radius = 12.dp, padding = PaddingValues(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ph.Eye, null, tint = t.accent, modifier = Modifier.size(18.dp)); HSpace(11.dp)
            Text("Be visible on this network", color = t.text, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            NSwitch(visible, onVisible, width = 48.dp, height = 28.dp)
        }
    }
}
