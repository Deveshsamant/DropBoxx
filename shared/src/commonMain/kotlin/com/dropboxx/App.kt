package com.dropboxx

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dropboxx.domain.PlatformServices
import com.dropboxx.model.ItemKind
import com.dropboxx.ui.AppEvent
import com.dropboxx.ui.AppViewModel
import com.dropboxx.ui.FileOpenerHost
import com.dropboxx.ui.box.BoxScreen
import com.dropboxx.ui.devices.DevicesScreen
import com.dropboxx.ui.devices.PeerBoxScreen
import com.dropboxx.ui.nav.Route
import com.dropboxx.ui.receive.AccessRequestDialog
import com.dropboxx.ui.receive.IncomingRequestDialog
import com.dropboxx.ui.rememberFileOpener
import com.dropboxx.ui.settings.SettingsScreen
import com.dropboxx.ui.theme.AppIcons
import com.dropboxx.ui.theme.DropBoxxTheme
import com.dropboxx.ui.transfers.TransfersScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private data class Tab(val route: Route, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.Box, "My box", AppIcons.Inbox),
    Tab(Route.Devices, "Devices", AppIcons.Wifi),
    Tab(Route.Transfers, "Transfers", AppIcons.History),
    Tab(Route.Settings, "Settings", Icons.Default.Settings),
)

/** Root of the shared UI. Koin must already be started by the platform entry point. */
@Composable
fun App(appViewModel: AppViewModel = koinViewModel()) {
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    DropBoxxTheme(themeMode) {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints {
                AppScaffold(wide = maxWidth >= 840.dp, appViewModel)
            }
        }
    }
}

@Composable
private fun AppScaffold(wide: Boolean, appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val platform = koinInject<PlatformServices>()
    val opener = rememberFileOpener(platform)
    val incoming by appViewModel.incomingRequest.collectAsStateWithLifecycle()
    val access by appViewModel.accessRequest.collectAsStateWithLifecycle()

    LaunchedEffect(appViewModel) {
        appViewModel.events.collect { event ->
            when (event) {
                is AppEvent.Toast -> snackbar.showSnackbar(event.message, duration = SnackbarDuration.Short)
                is AppEvent.Received -> {
                    val c = event.content
                    if (appViewModel.autoCopyText) clipboard.setText(AnnotatedString(c.content))
                    val label = if (c.kind == ItemKind.URL) "Link" else "Text"
                    val action = if (c.kind == ItemKind.URL) "Open" else if (appViewModel.autoCopyText) null else "Copy"
                    val result = snackbar.showSnackbar(
                        "$label from ${c.from.alias}" + if (appViewModel.autoCopyText) " copied to clipboard" else "",
                        actionLabel = action, duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        if (c.kind == ItemKind.URL) appViewModel.openUrl(c.content) else clipboard.setText(AnnotatedString(c.content))
                    }
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
        return dest.hasRoute(tab.route::class) || (tab.route == Route.Devices && dest.hasRoute(Route.PeerBox::class))
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(navController, startDestination = Route.Box, modifier = modifier) {
            composable<Route.Box> { BoxScreen(wide, opener, onMessage = appViewModel::toast) }
            composable<Route.Devices> { DevicesScreen(wide, onOpenPeer = { navController.navigate(Route.PeerBox(it.id)) }, onMessage = appViewModel::toast) }
            composable<Route.PeerBox> { entry ->
                PeerBoxScreen(entry.toRoute<Route.PeerBox>().peerId, wide, onBack = { navController.popBackStack() }, onMessage = appViewModel::toast)
            }
            composable<Route.Transfers> { TransfersScreen(opener) }
            composable<Route.Settings> { SettingsScreen() }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!wide) NavigationBar {
                tabs.forEach { tab -> NavigationBarItem(selected = selected(tab), onClick = { navigateTab(tab.route) }, icon = { Icon(tab.icon, null) }, label = { Text(tab.label) }) }
            }
        },
    ) { padding ->
        if (wide) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                NavigationRail {
                    tabs.forEach { tab -> NavigationRailItem(selected = selected(tab), onClick = { navigateTab(tab.route) }, icon = { Icon(tab.icon, null) }, label = { Text(tab.label) }) }
                }
                content(Modifier.fillMaxSize())
            }
        } else {
            content(Modifier.fillMaxSize().padding(padding))
        }
    }

    incoming?.let { request -> IncomingRequestDialog(request) { accept, trust -> appViewModel.respond(request, accept, trust) } }
    access?.let { request -> AccessRequestDialog(request) { allow, always -> appViewModel.respondAccess(request, allow, always) } }
    FileOpenerHost(opener, platform)
}
