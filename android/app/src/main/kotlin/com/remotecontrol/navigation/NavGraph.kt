package com.remotecontrol.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.remotecontrol.App
import com.remotecontrol.ui.commands.CommandsScreen
import com.remotecontrol.ui.sessions.SessionsScreen
import com.remotecontrol.ui.settings.SettingsScreen
import com.remotecontrol.ui.terminal.TerminalScreen

@Composable
fun NavGraph(app: App) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "sessions") {
        composable("sessions") {
            SessionsScreen(
                app = app,
                onSessionClick = { sessionName -> navController.navigate("terminal/$sessionName") },
                onCommandsClick = { navController.navigate("commands") },
                onSettingsClick = { navController.navigate("settings") },
            )
        }
        composable(
            route = "terminal/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            TerminalScreen(app = app, sessionId = sessionId, onBack = { navController.popBackStack() })
        }
        composable("commands") {
            CommandsScreen(app = app, onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(app = app, onBack = { navController.popBackStack() })
        }
    }
}
