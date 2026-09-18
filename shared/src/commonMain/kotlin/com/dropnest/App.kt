package com.dropnest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dropnest.domain.BoxRepository
import com.dropnest.domain.ChatService
import com.dropnest.domain.DiscoveryService
import com.dropnest.domain.LocalServer
import com.dropnest.domain.PlatformServices
import com.dropnest.domain.ServerState
import com.dropnest.domain.TransferEngine
import com.dropnest.model.ItemKind
import com.dropnest.ui.AppEvent
import com.dropnest.ui.AppViewModel
import com.dropnest.ui.FileOpenerHost
import com.dropnest.ui.box.BoxScreen
import com.dropnest.ui.chat.ChatScreen
import com.dropnest.ui.chat.ChatsScreen
import com.dropnest.ui.components.AccentTile
import com.dropnest.ui.components.HSpace
import com.dropnest.ui.components.Kicker
import com.dropnest.ui.components.Muted
import com.dropnest.ui.components.NButton
import com.dropnest.ui.components.NButtonStyle
import com.dropnest.ui.components.NCard
import com.dropnest.ui.components.VSpace
import com.dropnest.ui.components.phIcon
import com.dropnest.ui.devices.DevicesScreen
import com.dropnest.ui.devices.PeerBoxScreen
import com.dropnest.ui.motion.screenEnter
import com.dropnest.ui.motion.screenExit
import com.dropnest.ui.nav.Route
import com.dropnest.ui.onboarding.OnboardingScreen
import com.dropnest.ui.receive.AccessRequestDialog
import com.dropnest.ui.receive.IncomingRequestDialog
import com.dropnest.ui.receive.NocturneToast
import com.dropnest.ui.rememberFileOpener
import com.dropnest.ui.settings.SettingsScreen
import com.dropnest.ui.theme.DropNestTheme
import com.dropnest.ui.theme.N
import com.dropnest.ui.theme.Ph
import com.dropnest.ui.transfers.TransfersScreen
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private data class Tab(val route: Route, val label: String, val icon: ImageVector)

/** True on desktop windows wider than 1100 dp: side columns take their comfortable width. */
val LocalWindowWide = compositionLocalOf { true }

private val tabs = listOf(
    Tab(Route.Box, "My box", Ph.Cube),
    Tab(Route.Devices, "Devices", Ph.Wifi),
    Tab(Route.Chats, "Chats", Ph.ChatCircle),
    Tab(Route.Transfers, "Transfers", Ph.Clock),
    Tab(Route.Settings, "Settings", Ph.Gear),
)

/**
 * Root of the shared UI. Koin must already be started by the platform entry point.
 * [topBar] is drawn above everything inside the theme (desktop uses it for its own window chrome).
 */
@Composable
fun App(topBar: (@Composable () -> Unit)? = null, appViewModel: AppViewModel = koinViewModel()) {
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    val motion by appViewModel.motion.collectAsStateWithLifecycle()
    val onboarded by appViewModel.onboardingDone.collectAsStateWithLifecycle()
    DropNestTheme(themeMode) {
        val t = N
        Box(
            Modifier.fillMaxSize().background(t.bg)
                .background(Brush.radialGradient(listOf(t.bg2, Color.Transparent), center = Offset(0f, 0f), radius = 1100f))
                .background(Brush.radialGradient(listOf(t.surface.copy(alpha = 0.5f), Color.Transparent), center = Offset(Float.MAX_VALUE, 0f), radius = 900f)),
        ) {
            Column(Modifier.fillMaxSize()) {
                topBar?.invoke()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 840.dp
                CompositionLocalProvider(LocalWindowWide provides (maxWidth >= 1100.dp)) {
                if (!onboarded) {
                    val server by appViewModel.serverState.collectAsStateWithLifecycle()
                    val me by appViewModel.me.collectAsStateWithLifecycle()
                    val localServer = koinInject<LocalServer>()
                    OnboardingScreen(
                        wide, motion, initialName = me.alias, visible = server is ServerState.Running || server == ServerState.Starting,
                        onVisible = { if (it) localServer.start() else localServer.stop() },
                        onDone = appViewModel::finishOnboarding,
                    )
                } else {
                    AppScaffold(wide, motion, appViewModel)
                }
                }
                }
            }
        }
    }
}

@Composable
private fun AppScaffold(wide: Boolean, motion: Boolean, appViewModel: AppViewModel) {
    val t = N
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val clipboard = LocalClipboardManager.current
    val platform = koinInject<PlatformServices>()
    val opener = rememberFileOpener(platform)
    val incoming by appViewModel.incomingRequest.collectAsStateWithLifecycle()
    val access by appViewModel.accessRequest.collectAsStateWithLifecycle()
    var toast by remember { mutableStateOf<String?>(null) }
    var toastSeq by remember { mutableStateOf(0) }
    val showToast: (String) -> Unit = { toast = it; toastSeq++ }
    LaunchedEffect(toastSeq) { if (toast != null) { delay(2200); toast = null } }

    val chat = koinInject<ChatService>()
    LaunchedEffect(chat) {
        chat.incoming.collect { m -> if (chat.activeThread.value != m.peerId) showToast("Message from ${chat.conversations.value.firstOrNull { it.peer.id == m.peerId }?.peer?.alias ?: "a device"}: ${m.text.take(60)}") }
    }
    LaunchedEffect(appViewModel) {
        appViewModel.events.collect { event ->
            when (event) {
                is AppEvent.Toast -> showToast(event.message)
                is AppEvent.Received -> {
                    val c = event.content
                    if (appViewModel.autoCopyText) clipboard.setText(AnnotatedString(c.content))
                    showToast((if (c.kind == ItemKind.URL) "Link" else "Text") + " from ${c.from.alias}" + if (appViewModel.autoCopyText) " · copied" else "")
                }
            }
        }
    }

    fun navigateTab(route: Route) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun selected(tab: Tab): Boolean {
        val dest = backStack?.destination ?: return false
        return dest.hasRoute(tab.route::class) || (tab.route == Route.Devices && dest.hasRoute(Route.PeerBox::class)) || (tab.route == Route.Chats && dest.hasRoute(Route.Chat::class))
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(
            navController, startDestination = Route.Box, modifier = modifier,
            enterTransition = { screenEnter }, exitTransition = { screenExit }, popEnterTransition = { screenEnter }, popExitTransition = { screenExit },
        ) {
            composable<Route.Box> { BoxScreen(wide, motion, opener, onMessage = showToast, onOpenSettings = { navigateTab(Route.Settings) }) }
            composable<Route.Devices> { DevicesScreen(wide, motion, onOpenPeer = { navController.navigate(Route.PeerBox(it.id)) }, onMessage = showToast) }
            composable<Route.PeerBox> { entry -> PeerBoxScreen(entry.toRoute<Route.PeerBox>().peerId, wide, onBack = { navController.popBackStack() }, onMessage = showToast) }
            composable<Route.Chats> {
                ChatsScreen(wide, motion, opener, onOpenChat = { navController.navigate(Route.Chat(it)) }, onOpenPeerBox = { navController.navigate(Route.PeerBox(it.id)) }, onMessage = showToast)
            }
            composable<Route.Chat> { entry -> ChatScreen(entry.toRoute<Route.Chat>().peerId, wide, opener = opener, onBack = { navController.popBackStack() }, onMessage = showToast) }
            composable<Route.Transfers> { TransfersScreen(wide, motion, opener) }
            composable<Route.Settings> { SettingsScreen(wide, onReplayTour = {}) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(appViewModel, ::selected, ::navigateTab)
                content(Modifier.fillMaxSize())
            }
        } else {
            // The keyboard pushes the whole column up and the tab bar steps aside, so an input
            // sits directly on the keyboard instead of a tab-bar-high gap above it.
            @OptIn(ExperimentalLayoutApi::class)
            val imeVisible = WindowInsets.isImeVisible
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    content(Modifier.fillMaxSize())
                    NocturneToast(toast, Modifier.align(Alignment.BottomCenter))
                }
                if (!imeVisible) PhoneNav(::selected, ::navigateTab)
            }
        }
        incoming?.let { IncomingRequestDialog(it, wide, motion) { accept, trust -> appViewModel.respond(it, accept, trust) } }
        access?.let { AccessRequestDialog(it, wide, motion) { allow, always -> appViewModel.respondAccess(it, allow, always) } }
        if (wide) NocturneToast(toast, Modifier.align(Alignment.BottomCenter))
        FileOpenerHost(opener, platform)
    }
}

/** Desktop rail: this device, the four sections with counts, a tip card at the bottom. */
@Composable
private fun Sidebar(appViewModel: AppViewModel, selected: (Tab) -> Boolean, navigate: (Route) -> Unit) {
    val t = N
    val me by appViewModel.me.collectAsStateWithLifecycle()
    val server by appViewModel.serverState.collectAsStateWithLifecycle()
    val boxCount by koinInject<BoxRepository>().items.collectAsStateWithLifecycle()
    val peers by koinInject<DiscoveryService>().peers.collectAsStateWithLifecycle()
    val sessions by koinInject<TransferEngine>().sessions.collectAsStateWithLifecycle()
    val live = sessions.count { !it.status.isTerminal }
    val unread = koinInject<ChatService>().conversations.collectAsStateWithLifecycle().value.sumOf { it.unread }
    Column(Modifier.width(if (LocalWindowWide.current) 212.dp else 188.dp).fillMaxHeight().background(t.bg2).padding(11.dp, 15.dp)) {
        Box(Modifier.fillMaxWidth().padding(7.dp, 0.dp, 7.dp, 11.dp)) { Kicker("This device", accent = false, size = 10) }
        NCard(Modifier.fillMaxWidth(), radius = 10.dp, padding = PaddingValues(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentTile(me.deviceType.phIcon(), size = 32.dp, iconSize = 17.dp, radius = 8.dp)
                HSpace(9.dp)
                Column {
                    Text(me.alias, color = t.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    val s = server
                    Text(
                        when (s) { is ServerState.Running -> "Visible · ${s.addresses.firstOrNull() ?: "no network"}"; ServerState.Starting -> "Starting…"; ServerState.Stopped -> "Hidden · not discoverable"; is ServerState.Failed -> s.message },
                        color = if (s is ServerState.Running) t.accent else t.muted, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1,
                    )
                }
            }
        }
        VSpace(16.dp)
        tabs.forEach { tab ->
            val count = when (tab.route) { Route.Box -> boxCount.size; Route.Devices -> peers.size; else -> 0 }
            NButton(
                tab.label, { navigate(tab.route) }, Modifier.fillMaxWidth().padding(vertical = 1.dp), style = NButtonStyle.Nav, icon = tab.icon, iconSize = 18.dp,
                active = selected(tab), fontSize = 13, padding = PaddingValues(10.dp, 9.dp),
                trailing = {
                    Spacer(Modifier.weight(1f))
                    if (count > 0) Muted("$count", 10)
                    if (tab.route == Route.Transfers && live > 0) Text("$live", color = t.accent, fontSize = 10.sp, modifier = Modifier.clip(CircleShape).background(t.accentSoft).padding(6.dp, 1.dp))
                    if (tab.route == Route.Chats && unread > 0) Text("$unread", color = t.bg, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).background(t.accent).padding(6.dp, 1.dp))
                },
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(t.accentSoft, Color.Transparent)))
                .border(1.dp, t.accentLine, RoundedCornerShape(11.dp)).padding(11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ph.Lightning, null, tint = t.accent, modifier = Modifier.height(14.dp))
                HSpace(6.dp)
                Text("No router around?", color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            VSpace(4.dp)
            Muted("Turn on hotspot mode on your phone and join its network from here — DropNest finds it in seconds.", 10)
        }
    }
}

/** Phone bottom bar: four column buttons, the active one gets the accent-soft pill. */
@Composable
private fun PhoneNav(selected: (Tab) -> Boolean, navigate: (Route) -> Unit) {
    val t = N
    val live = koinInject<TransferEngine>().sessions.collectAsStateWithLifecycle().value.count { !it.status.isTerminal }
    val unread = koinInject<ChatService>().conversations.collectAsStateWithLifecycle().value.sumOf { it.unread }
    Row(Modifier.fillMaxWidth().background(t.bg2).navigationBarsPadding().padding(10.dp, 7.dp, 10.dp, 10.dp)) {
        tabs.forEach { tab ->
            val on = selected(tab)
            Column(
                Modifier.weight(1f).padding(horizontal = 1.5.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) t.accentSoft else Color.Transparent)
                    .clickable { navigate(tab.route) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Icon(tab.icon, tab.label, tint = if (on) t.accent else t.text, modifier = Modifier.height(21.dp).width(21.dp))
                    if (tab.route == Route.Transfers && live > 0) Box(Modifier.align(Alignment.TopEnd).width(7.dp).height(7.dp).clip(CircleShape).background(t.accent))
                    if (tab.route == Route.Chats && unread > 0) Text("$unread", color = t.bg, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).clip(CircleShape).background(t.accent).padding(4.dp, 1.dp))
                }
                VSpace(3.dp)
                Text(tab.label, color = if (on) t.accent else t.text, fontSize = 10.5.sp, lineHeight = 12.sp)
            }
        }
    }
}
