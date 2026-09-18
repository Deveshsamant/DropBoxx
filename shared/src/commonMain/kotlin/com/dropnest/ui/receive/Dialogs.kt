package com.dropnest.ui.receive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dropnest.core.formatBytes
import com.dropnest.model.AccessRequest
import com.dropnest.model.IncomingRequest
import com.dropnest.ui.components.AccentTile
import com.dropnest.ui.components.HSpace
import com.dropnest.ui.components.Muted
import com.dropnest.ui.components.NButton
import com.dropnest.ui.components.NButtonStyle
import com.dropnest.ui.components.VSpace
import com.dropnest.ui.components.phIcon
import com.dropnest.ui.motion.bobOffset
import com.dropnest.ui.motion.dialogEnter
import com.dropnest.ui.motion.sheetEnter
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph
import kotlin.math.roundToInt

/**
 * Prototype `dkDialog` / `phSheetWrap`: on wide layouts a centered dialog (apDialog), on phones a
 * bottom sheet (apSheet). Both float over a dimmed backdrop and cannot be dismissed by a stray tap.
 */
@Composable
fun NocturneOverlay(visible: Boolean, wide: Boolean, content: @Composable () -> Unit) {
    val t = N
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(t.bg.copy(alpha = 0.62f)).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
            if (wide) {
                Box(Modifier.align(Alignment.Center).padding(20.dp)) {
                    AnimatedVisibility(true, enter = dialogEnter) {
                        Column(
                            Modifier.widthIn(max = 430.dp).shadow(22.dp, RoundedCornerShape(16.dp), ambientColor = t.shadow, spotColor = t.shadow)
                                .clip(RoundedCornerShape(16.dp)).background(t.surface).border(1.dp, t.edgeStrong, RoundedCornerShape(16.dp)).padding(18.dp),
                        ) { content() }
                    }
                }
            } else {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    AnimatedVisibility(true, enter = sheetEnter) {
                        Column(
                            Modifier.fillMaxWidth().shadow(22.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), ambientColor = t.shadow, spotColor = t.shadow)
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(t.surface).padding(16.dp, 16.dp, 16.dp, 22.dp),
                        ) {
                            Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 4.dp).clip(CircleShape).background(t.line))
                            VSpace(14.dp)
                            content()
                        }
                    }
                }
            }
        }
    }
}

/** Three stacked cards with the front one bobbing — the "incoming drop" illustration. */
@Composable
private fun StackIllustration(icon: ImageVector, motion: Boolean = true) {
    val t = N
    val bob = if (motion) bobOffset(4f, 3400) else 0f
    Box(Modifier.fillMaxWidth().height(104.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.offset(x = (-38).dp, y = 7.dp).graphicsLayer { rotationZ = -9f; scaleX = 0.9f; scaleY = 0.9f }.size(84.dp, 60.dp).clip(RoundedCornerShape(9.dp)).background(t.bg2).border(1.dp, t.edge, RoundedCornerShape(9.dp)))
        Box(Modifier.offset(x = 20.dp, y = 4.dp).graphicsLayer { rotationZ = 7f; scaleX = 0.95f; scaleY = 0.95f }.size(84.dp, 60.dp).clip(RoundedCornerShape(9.dp)).background(t.surface2).border(1.dp, t.edge, RoundedCornerShape(9.dp)))
        Box(
            Modifier.offset { IntOffset(0, bob.roundToInt()) }.shadow(8.dp, RoundedCornerShape(9.dp), ambientColor = t.shadow, spotColor = t.shadow)
                .size(84.dp, 60.dp).clip(RoundedCornerShape(9.dp)).background(t.surface).border(1.dp, t.edgeStrong, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = t.accent, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun EncryptedNote(text: String) {
    val t = N
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.bg2).padding(11.dp, 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ph.ShieldCheck, null, tint = t.accent, modifier = Modifier.size(16.dp)); HSpace(9.dp)
        Muted(text, 11)
    }
}

/** A peer wants to browse this device's box. */
@Composable
fun AccessRequestDialog(request: AccessRequest, wide: Boolean, motion: Boolean, onDecision: (allow: Boolean, always: Boolean) -> Unit) {
    val t = N
    NocturneOverlay(true, wide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentTile(request.requester.deviceType.phIcon(), size = 42.dp, iconSize = 21.dp, radius = 11.dp)
            HSpace(11.dp)
            Column {
                Text("Someone wants in", color = t.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Muted("${request.requester.alias} wants to open your box · ${request.address}", 11)
            }
        }
        VSpace(13.dp)
        StackIllustration(Ph.Cube, motion)
        VSpace(13.dp)
        EncryptedNote("They will see your item list and can fetch items. Same network, end-to-end encrypted.")
        VSpace(13.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NButton("Deny", { onDecision(false, false) }, Modifier.weight(1f), padding = PaddingValues(12.dp, if (wide) 8.dp else 12.dp))
            NButton("Once", { onDecision(true, false) }, Modifier.weight(1f), padding = PaddingValues(12.dp, if (wide) 8.dp else 12.dp))
            NButton("Always allow", { onDecision(true, true) }, Modifier.weight(1.4f), style = NButtonStyle.Primary, icon = Ph.Check, padding = PaddingValues(12.dp, if (wide) 8.dp else 12.dp))
        }
    }
}

/** A peer is pushing items to us (kept for protocol completeness; the UI itself is pull-only). */
@Composable
fun IncomingRequestDialog(request: IncomingRequest, wide: Boolean, motion: Boolean, onDecision: (accept: Boolean, trust: Boolean) -> Unit) {
    val t = N
    NocturneOverlay(true, wide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentTile(Ph.ArrowDown, size = 42.dp, iconSize = 21.dp, radius = 11.dp)
            HSpace(11.dp)
            Column {
                Text("Incoming drop", color = t.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Muted("${request.sender.alias} wants to send ${request.items.size} item${if (request.items.size == 1) "" else "s"} · ${formatBytes(request.totalBytes)}", 11)
            }
        }
        VSpace(13.dp)
        StackIllustration(Ph.Image, motion)
        VSpace(13.dp)
        EncryptedNote("Same network, encrypted. Files land in your DropNest folder.")
        VSpace(13.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NButton("Decline", { onDecision(false, false) }, Modifier.weight(1f))
            NButton("Accept", { onDecision(true, false) }, Modifier.weight(1.4f), style = NButtonStyle.Primary, icon = Ph.Check)
        }
    }
}

/** Bottom-centre pill that slides up 22 px and fades — the prototype's toast. */
@Composable
fun NocturneToast(message: String?, modifier: Modifier = Modifier) {
    val t = N
    Box(modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            message != null,
            enter = fadeIn(tween(300)) + androidx.compose.animation.slideInVertically(tween(300)) { 22 },
            exit = fadeOut(tween(300)) + androidx.compose.animation.slideOutVertically(tween(300)) { 22 },
        ) {
            Text(
                message.orEmpty(), color = t.text, fontSize = 12.5.sp,
                modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = t.shadow, spotColor = t.shadow).clip(CircleShape).background(t.surface).border(1.dp, t.edgeStrong, CircleShape).padding(16.dp, 9.dp),
            )
        }
    }
}
