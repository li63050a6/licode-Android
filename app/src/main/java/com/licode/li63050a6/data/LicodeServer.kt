package com.licode.li63050a6.data

import android.content.Context
import com.licode.li63050a6.domain.RootfsConfig
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 本机 licode 服务器：把 app 内置的静态二进制在手机上直接运行。
 * 工作区 = 所选 rootfs 实例中的目录（进程启动目录即工作区），web 界面供局域网远程访问。
 */
class LicodeServer(private val context: Context, private val http: OkHttpClient) {

    private val baseDir get() = File(context.filesDir, "rootfs")

    fun binaryOf(id: String) = File(baseDir, "$id/bin/licode-server")
    fun licodeHome(id: String) = File(baseDir, "$id/env")

    /** 工作区目录（rootfs 内相对路径），并确保存在。 */
    fun workspaceOf(cfg: RootfsConfig): File {
        val ws = File(baseDir, "${cfg.id}/root/${cfg.workspaceSub}")
        ws.mkdirs()
        return ws
    }

    /** 把 assets 里的静态二进制复制到本机（已存在则跳过）。 */
    fun ensureBinary(id: String): Boolean = runCatching {
        val bin = binaryOf(id)
        if (bin.exists() && bin.length() > 1000 * 1000) return@runCatching true
        bin.parentFile?.mkdirs()
        context.assets.open("licode-server-arm64").use { input ->
            bin.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
        }
        bin.setExecutable(true)
        bin.setReadable(true)
        bin.setWritable(true)
        true
    }.getOrElse { false }

    /** 写入 config.toml（服务器监听与密码）。 */
    fun writeConfig(cfg: RootfsConfig) {
        val home = licodeHome(cfg.id)
        home.mkdirs()
        val toml = File(home, "config.toml")
        if (!toml.exists()) {
            toml.writeText(
                """
                # licode 本机服务器配置（由 App 生成）
                [server]
                host = "0.0.0.0"
                port = ${cfg.licodePort}
                addr = ""
                username = "licode"
                password = "${cfg.licodePassword.orEmpty()}"
                https = false
                tls_cert = ""
                tls_key = ""
                """.trimIndent() + "\n"
            )
        }
    }

    /** 把用户配置的 SSH 远程凭据写入工作区的 ~/.ssh（AI 通过 ssh 连远程服务器）。 */
    fun prepareSsh(cfg: RootfsConfig) {
        val host = cfg.sshHost ?: return
        if (cfg.sshKey.isNullOrBlank()) return
        val ws = workspaceOf(cfg)
        val sshDir = File(ws, ".ssh")
        sshDir.mkdirs()
        File(sshDir, "id_rsa").apply {
            writeText(cfg.sshKey.trimEnd() + "\n")
            setReadable(true, true)
            setWritable(true, true)
            setExecutable(false, false)
        }
        File(sshDir, "config").apply {
            writeText(
                "Host *\n" +
                    "  HostName $host\n" +
                    "  User ${cfg.sshUser ?: "root"}\n" +
                    "  Port ${cfg.sshPort}\n" +
                    "  StrictHostKeyChecking no\n" +
                    "  UserKnownHostsFile /dev/null\n" +
                    "  IdentitiesOnly yes\n" +
                    "  IdentityFile ~/.ssh/id_rsa\n"
            )
        }
        Shell.sh("chmod 600 '$sshDir/id_rsa' '$sshDir/config'")
    }

    /** 启动 licode：直接在本机（root）运行，cwd 指向 rootfs 工作区。 */
    suspend fun start(cfg: RootfsConfig): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        ensureBinary(cfg.id)
        writeConfig(cfg)
        prepareSsh(cfg)
        val bin = binaryOf(cfg.id)
        if (!bin.exists()) {
            return@withContext(false to "无法解压服务器程序")
        }
        val ws = workspaceOf(cfg)
        val home = licodeHome(cfg.id)
        home.mkdirs()
        val log = File(home, "server.log")
        if (Shell.suPath() == null) {
            return@withContext(false to "未检测到 root（需要 Magisk/SuperSU）")
        }
        // cwd = rootfs 工作区；PATH 注入 rootfs 的 bin，使 Shell 工具在内可用 ssh 等命令
        val rootFs = File(baseDir, "${cfg.id}/root").absolutePath
        val cmd = "cd '$ws' && export HOME='$ws' LICODE_HOME='$home' " +
            "PATH='$rootFs/usr/bin:$rootFs/bin:/system/bin:/system/xbin:\$PATH' && " +
            "nohup '$bin' web --host 0.0.0.0 --port ${cfg.licodePort}" +
            (cfg.licodePassword?.let { " --password '$it'" } ?: "") +
            " -c '$home/config.toml' > '$log' 2>&1 &"
        val (ok, out) = Shell.su("sh -c \"$cmd\"")
        if (ok) {
            // 等待健康检查
            for (i in 0 until 20) {
                if (healthOk(cfg.licodePort)) {
                    return@withContext(true to "本机服务器已就绪")
                }
                Thread.sleep(300)
            }
            (false to "进程已启动但健康检查未通过：\n" + readLog(cfg).takeLast(500))
        } else {
            false to out
        }
    }

    /** 停止本机 licode。 */
    suspend fun stop(cfg: RootfsConfig) = withContext(Dispatchers.IO) {
        Shell.su("pkill -f '${binaryOf(cfg.id)} web '")
    }

    /** 探测本机服务是否在运行。 */
    suspend fun healthOk(port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("http://127.0.0.1:$port/health").build()
            http.newCall(req).execute().use { it.isSuccessful || it.code in 400..499 }
        } catch (e: Exception) {
            false
        }
    }

    /** 读取服务日志（最后 8KB）。 */
    fun readLog(cfg: RootfsConfig): String {
        val log = File(licodeHome(cfg.id), "server.log")
        if (!log.exists()) return ""
        val text = log.readText()
        return text.takeLast(8000)
    }

    /** 局域网 IPv4 地址列表。 */
    fun lanAddresses(): List<String> = runCatching {
        val out = mutableListOf<String>()
        val enums = NetworkInterface.getNetworkInterfaces()
        while (enums.hasMoreElements()) {
            val nif = enums.nextElement()
            if (!nif.isUp || nif.isLoopback) continue
            val addr = nif.inetAddresses
            while (addr.hasMoreElements()) {
                val a = addr.nextElement()
                if (a is Inet4Address && !a.isLoopbackAddress) {
                    out += a.hostAddress ?: continue
                }
            }
        }
        out
    }.getOrDefault(emptyList())
}