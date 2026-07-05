package com.syncbridge.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.syncbridge.app.ui.connections.AddConnectionScreen
import com.syncbridge.app.ui.connections.RemoteFolderBrowserScreen
import com.syncbridge.app.ui.connections.SavedConnectionsScreen
import com.syncbridge.app.ui.dashboard.DashboardScreen
import com.syncbridge.app.ui.history.SyncHistoryScreen
import com.syncbridge.app.ui.livesync.LiveSyncScreen
import com.syncbridge.app.ui.profile.CreateSyncProfileScreen
import com.syncbridge.app.ui.profile.ProfileDetailsScreen
import com.syncbridge.app.ui.settings.SettingsScreen

@Composable
fun SyncBridgeNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Destinations.DASHBOARD) {
        composable(Destinations.DASHBOARD) {
            DashboardScreen(
                onAddProfile = { navController.navigate(Destinations.createSyncProfile()) },
                onOpenProfile = { profileId -> navController.navigate(Destinations.profileDetails(profileId)) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
            )
        }

        composable(Destinations.SAVED_CONNECTIONS) {
            SavedConnectionsScreen(
                onBack = { navController.popBackStack() },
                onAddConnection = { navController.navigate(Destinations.addConnection()) },
                onEditConnection = { id -> navController.navigate(Destinations.addConnection(id)) },
            )
        }

        composable(
            route = Destinations.ADD_CONNECTION,
            arguments = listOf(navArgument(Destinations.ARG_CONNECTION_ID) { type = NavType.LongType; defaultValue = -1L }),
        ) {
            AddConnectionScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = Destinations.REMOTE_FOLDER_BROWSER,
            arguments = listOf(navArgument(Destinations.ARG_CONNECTION_ID) { type = NavType.LongType }),
        ) {
            RemoteFolderBrowserScreen(
                onBack = { navController.popBackStack() },
                onFolderSelected = { path ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(Destinations.RESULT_REMOTE_PATH, path)
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Destinations.CREATE_SYNC_PROFILE,
            arguments = listOf(navArgument(Destinations.ARG_PROFILE_ID) { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val remotePath by entry.savedStateHandle
                .getStateFlow<String?>(Destinations.RESULT_REMOTE_PATH, null)
                .collectAsState()

            CreateSyncProfileScreen(
                pickedRemotePath = remotePath,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onPickRemoteFolder = { connectionId -> navController.navigate(Destinations.remoteFolderBrowser(connectionId)) },
                onAddConnection = { navController.navigate(Destinations.addConnection()) },
            )
        }

        composable(
            route = Destinations.PROFILE_DETAILS,
            arguments = listOf(navArgument(Destinations.ARG_PROFILE_ID) { type = NavType.LongType }),
        ) {
            ProfileDetailsScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Destinations.createSyncProfile(id)) },
                onOpenLiveSync = { id -> navController.navigate(Destinations.liveSync(id)) },
                onOpenHistory = { id -> navController.navigate(Destinations.syncHistory(id)) },
            )
        }

        composable(
            route = Destinations.LIVE_SYNC,
            arguments = listOf(navArgument(Destinations.ARG_PROFILE_ID) { type = NavType.LongType }),
        ) { entry ->
            val profileId = entry.arguments?.getLong(Destinations.ARG_PROFILE_ID) ?: return@composable
            LiveSyncScreen(profileId = profileId, onBack = { navController.popBackStack() })
        }

        composable(
            route = Destinations.SYNC_HISTORY,
            arguments = listOf(navArgument(Destinations.ARG_PROFILE_ID) { type = NavType.LongType }),
        ) {
            SyncHistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onManageConnections = { navController.navigate(Destinations.SAVED_CONNECTIONS) },
            )
        }
    }
}
