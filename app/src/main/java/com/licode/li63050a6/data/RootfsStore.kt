package com.licode.li63050a6.data

import android.content.SharedPreferences
import com.licode.li63050a6.domain.RootfsConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** rootfs 实例列表的持久化。 */
class RootfsStore(private val prefs: SharedPreferences) {

    private val key = "rootfs_instances"
    private val json = Json { ignoreUnknownKeys = true }

    fun instances(): List<RootfsConfig> =
        json.decodeFromString<List<RootfsConfig>>(prefs.getString(key, "[]")!!)

    fun add(cfg: RootfsConfig) {
        val list = instances().toMutableList()
        list += cfg
        save(list)
    }

    fun update(cfg: RootfsConfig) {
        val list = instances().map { if (it.id == cfg.id) cfg else it }
        save(list)
    }

    fun remove(id: String) {
        val list = instances().filterNot { it.id == id }
        save(list)
    }

    /** 记录最近一次使用的实例（重启服务时恢复）。 */
    fun setActiveId(id: String?) {
        prefs.edit().putString("rootfs_active_id", id).apply()
    }

    fun activeId(): String? = prefs.getString("rootfs_active_id", null)

    private fun save(list: List<RootfsConfig>) {
        prefs.edit().putString(key, json.encodeToString(list)).apply()
    }
}