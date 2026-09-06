package com.licode.li63050a6.ui.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.licode.li63050a6.LicodeApp
import com.licode.li63050a6.data.AppSettings
import com.licode.li63050a6.ui.chat.ChatScreen
import com.licode.li63050a6.ui.login.LoginScreen
import com.licode.li63050a6.ui.rootfs.RootfsScreen
import com.licode.li63050a6.ui.servers.ServersScreen
import com.licode.li63050a6.ui.settings.SettingsScreen

/** 应用导航 + 首次启动权限申请。 */
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = remember { (context.applicationContext as LicodeApp).container }
    val settingsStore = container.settingsStore

    var settings by remember { mutableStateOf(settingsStore.load()) }

    // 首次启动：申请存储 + 麦克风权限
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        settings = settings.copy(storagePermissionGranted = ok, firstLaunch = false)
        settingsStore.save(settings)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        settings = settings.copy(micPermissionGranted = ok)
        settingsStore.save(settings)
    }

    LaunchedEffect(Unit) {
        if (settings.firstLaunch) {
            storageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
                onLocalServer = {
                    nav.navigate("rootfs") { launchSingleTop = true }
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
            ChatScreen(
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate("settings") },
            )
        }

        composable("rootfs") {
            RootfsScreen(onBack = { nav.popBackStack() })
        }

        composable("settings") {
            SettingsScreen(
                settings = settings,
                onBack = { nav.popBackStack() },
                onSettingsChanged = { newSettings ->
                    settings = newSettings
                    settingsStore.save(newSettings)
                },
            )
        }
    }
}