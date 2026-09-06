package com.licode.li63050a6.di

import android.content.Context
import android.content.SharedPreferences
import com.licode.li63050a6.data.LicodeHttp
import com.licode.li63050a6.data.ServerStore

/** 手动依赖注入容器（Application 级，避免引入 Hilt/Koin 等额外依赖）。 */
class AppContainer(context: Context) {
    // 应用级共享偏好，用于服务器列表与 Cookie 持久化
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("licode", Context.MODE_PRIVATE)

    val serverStore = ServerStore(prefs)
    val http = LicodeHttp(prefs)
}