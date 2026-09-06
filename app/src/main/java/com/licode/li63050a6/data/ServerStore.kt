package com.licode.li63050a6.data

import android.content.SharedPreferences
import com.licode.li63050a6.domain.ServerConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 服务器配置持久化（JSON 列表 + 当前选中）。 */
class ServerStore(private val prefs: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true }

    fun servers(): List<ServerConfig> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<ServerConfig>>(raw)
        }.getOrDefault(emptyList())
    }

    fun saveServers(list: List<ServerConfig>) {
        prefs.edit().putString(KEY_SERVERS, json.encodeToString(list)).apply()
    }

    fun add(server: ServerConfig) {
        val list = servers().toMutableList()
        list.add(server)
        saveServers(list)
    }

    fun update(server: ServerConfig) {
        val list = servers().toMutableList()
        val i = list.indexOfFirst { it.id == server.id }
        if (i >= 0) list[i] = server
        saveServers(list)
    }

    fun remove(id: String) {
        saveServers(servers().filterNot { it.id == id })
        if (currentId() == id) {
            prefs.edit().remove(KEY_CURRENT).apply()
        }
    }

    fun currentId(): String? = prefs.getString(KEY_CURRENT, null)

    fun current(): ServerConfig? = servers().firstOrNull { it.id == currentId() }

    fun setCurrent(id: String) {
        prefs.edit().putString(KEY_CURRENT, id).apply()
    }

    private companion object {
        const val KEY_SERVERS = "servers"
        const val KEY_CURRENT = "current"
    }
}