package com.minhquanghp86.frigatenvr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.minhquanghp86.frigatenvr.FrigateApp
import com.minhquanghp86.frigatenvr.data.FrigateRepository
import com.minhquanghp86.frigatenvr.data.SessionState
import com.minhquanghp86.frigatenvr.ui.screens.CameraLiveScreen
import com.minhquanghp86.frigatenvr.ui.screens.CamerasScreen
import com.minhquanghp86.frigatenvr.ui.screens.EventDetailScreen
import com.minhquanghp86.frigatenvr.ui.screens.EventsScreen
import com.minhquanghp86.frigatenvr.ui.screens.LoginScreen
import com.minhquanghp86.frigatenvr.ui.screens.ReviewScreen
import com.minhquanghp86.frigatenvr.ui.screens.SettingsScreen
import com.minhquanghp86.frigatenvr.ui.screens.TimelineScreen

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab("cameras", "Camera"),
    Tab("events", "Sự kiện"),
    Tab("review", "Cần xem"),
    Tab("timeline", "Xem lại"),
    Tab("settings", "Cài đặt"),
)

@Composable
fun AppRoot() {
    val app = LocalContext.current.applicationContext as FrigateApp
    val repo = app.container.repository
    val state by repo.state.collectAsState()
    val notice by repo.notice.collectAsState()
    val notifyEnabled by repo.notifyEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Giữ foreground service theo cài đặt (khởi động lại sau khi app bị kill).
    LaunchedEffect(notifyEnabled, state) {
        val ctx = app.applicationContext
        val ready = state is SessionState.Ready
        val intent = android.content.Intent(
            ctx,
            com.minhquanghp86.frigatenvr.notify.EventWatchService::class.java,
        )
        if (notifyEnabled && ready) {
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        } else if (!notifyEnabled) {
            ctx.stopService(intent)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            SessionState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            SessionState.Anonymous -> LoginScreen(repo)

            is SessionState.Ready -> MainScaffold(repo, s)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            repo.noticeShown()
        }
    }
}

@Composable
private fun MainScaffold(repo: FrigateRepository, state: SessionState.Ready) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onDetail = route != null && route !in tabs.map { it.route }

    // Điều hướng từ thông báo / deep link (mở đúng sự kiện).
    val pendingRoute by com.minhquanghp86.frigatenvr.notify.DeepLinks.route.collectAsState()
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { target ->
            navController.navigate(target) { launchSingleTop = true }
            com.minhquanghp86.frigatenvr.notify.DeepLinks.clear()
        }
    }

    Scaffold(
        containerColor = IOSBackground,
        bottomBar = {
            if (!onDetail) {
                NavigationBar(
                    containerColor = Color(0xF20B0B0C),
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = IOSBlue,
                                selectedTextColor = IOSBlue,
                                unselectedIconColor = IOSGray,
                                unselectedTextColor = IOSGray,
                                indicatorColor = Color.Transparent,
                            ),
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab.route) {
                                        "cameras" -> Icons.Outlined.Videocam
                                        "events" -> Icons.Outlined.Notifications
                                        "review" -> Icons.Outlined.History
                                        else -> Icons.Outlined.Settings
                                    },
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            label = { Text(tab.label, fontSize = 10.sp) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "cameras",
            modifier = Modifier
                .padding(padding)
                .background(IOSGroupedBackground),
        ) {
            composable("cameras") {
                CamerasScreen(
                    modifier = Modifier,
                    onOpenCamera = { navController.navigate("camera/$it") },
                )
            }
            composable(
                "camera/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                CameraLiveScreen(
                    modifier = Modifier,
                    camera = entry.arguments?.getString("name").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("events") {
                EventsScreen(
                    modifier = Modifier,
                    onOpenEvent = { navController.navigate("event/$it") },
                )
            }
            composable(
                "event/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                EventDetailScreen(
                    modifier = Modifier,
                    eventId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("review") {
                ReviewScreen(modifier = Modifier)
            }
            composable("timeline") {
                TimelineScreen(modifier = Modifier)
            }
            composable("settings") {
                SettingsScreen(
                    modifier = Modifier,
                    session = state.session,
                    onOpenLogs = { navController.navigate("logs") },
                    onOpenStorage = { navController.navigate("storage") },
                )
            }
            composable("logs") {
                com.minhquanghp86.frigatenvr.ui.screens.LogsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable("storage") {
                com.minhquanghp86.frigatenvr.ui.screens.StorageScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
