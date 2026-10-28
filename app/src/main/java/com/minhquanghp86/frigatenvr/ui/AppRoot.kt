package com.minhquanghp86.frigatenvr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(
        bottomBar = {
            if (!onDetail) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
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
                                )
                            },
                            label = { Text(tab.label, fontSize = 11.sp) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "cameras",
            modifier = Modifier.padding(padding),
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
                SettingsScreen(modifier = Modifier, session = state.session)
            }
        }
    }
}
