package com.licode.li63050a6.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 以 root（su）或普通 shell 执行命令。 */
object Shell {

    private val SU_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/data/adb/magisk/su", "/magisk/sepolicy/su"
    )

    /** 找到可用的 su 路径（执行 id 确认 uid=0），无则 null。 */
    fun suPath(): String? {
        for (p in SU_PATHS) {
            if (java.io.File(p).canExecute()) {
                val r = exec(p, "id")
                if (r.first == 0 && r.second.contains("uid=0")) return p
            }
        }
        return null
    }

    /** 是否有 root 权限。 */
    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) { suPath() != null }

    /** 以 root 执行一条命令，返回 (是否成功, 输出)。 */
    fun su(cmd: String): Pair<Boolean, String> {
        val path = suPath() ?: return false to "未检测到 root（需要 Magisk/SuperSU）"
        val r = exec(path, cmd)
        return (r.first == 0) to (r.second + if (r.first != 0) "\n(退出码 ${r.first})" else "")
    }

    /** 以普通用户 shell 执行。 */
    fun sh(cmd: String): Pair<Boolean, String> {
        val r = exec("/system/bin/sh", "-c", cmd)
        return (r.first == 0) to r.second
    }

    private fun exec(vararg cmd: String): Pair<Int, String> {
        return try {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            val code = p.waitFor()
            code to out.trim()
        } catch (e: Exception) {
            0 to "无法执行 ${cmd.joinToString(" ")}：${e.message}"
        }
    }
}