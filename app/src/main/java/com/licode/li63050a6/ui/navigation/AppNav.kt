package com.licode.li63050a6.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.licode.li63050a6.ui.chat.ChatScreen
import com.licode.li63050a6.ui.login.LoginScreen
import com.licode.li63050a6.ui.servers.ServersScreen

/** 应用导航：服务器列表 → 登录（需登录时）→ 对话。 */
@Composable
fun AppNav() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "servers") {
        composable("servers") {
            ServersScreen(
                onEnterChat = {
                    nav.navigate("chat") {
                        launchSingleTop = true
                        popUpTo("servers") { saveState = true }
                    }
                },
                onNeedLogin = { id ->
                    nav.navigate("login/$id") {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route = "login/{serverId}",
            arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
        ) { entry ->
            val serverId = entry.arguments?.getString("serverId").orEmpty()
            LoginScreen(
                serverId = serverId,
                onLoggedIn = {
                    nav.navigate("chat") {
                        launchSingleTop = true
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable("chat") {
            ChatScreen(onBack = { nav.popBackStack() })
        }
    }
}