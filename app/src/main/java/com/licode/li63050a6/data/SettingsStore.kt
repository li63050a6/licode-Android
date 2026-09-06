package com.licode.li63050a6.data

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 应用设置（背景色、背景图、权限状态等）。 */
@Serializable
data class AppSettings(
    val backgroundColor: String? = null,        // 背景色（十六进制，如 #FFFAFBFF）
    val backgroundImageUri: String? = null,     // 背景图 URI
    val storagePermissionGranted: Boolean = false,
    val micPermissionGranted: Boolean = false,
    val firstLaunch: Boolean = true,
)

class SettingsStore(private val prefs: SharedPreferences) {

    private val key = "app_settings"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AppSettings =
        json.decodeFromString<AppSettings>(prefs.getString(key, "{}")!!)

    fun save(s: AppSettings) {
        prefs.edit().putString(key, json.encodeToString(s)).apply()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        save(transform(load()))
    }
}