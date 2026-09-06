package com.licode.li63050a6.data

import android.content.Context
import com.licode.li63050a6.domain.RootfsConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

/** rootfs 实例管理：下载/解压/校验/删除。 */
class RootfsManager(private val context: Context, private val http: OkHttpClient) {

    val baseDir get() = File(context.filesDir, "rootfs")

    /** 实例的根文件系统目录。 */
    fun rootDir(id: String) = File(baseDir, "$id/root")

    /** 实例的 licode 数据目录（LICODE_HOME）。 */
    fun licodeHome(id: String) = File(baseDir, "$id/env")

    /** rootfs 的存档文件（下载中的 .pack 与完整版）。 */
    fun archiveFile(id: String) = File(baseDir, "$id/rootfs.tar")

    /** 是否已安装（有完整的根文件系统）。 */
    fun isInstalled(cfg: RootfsConfig): Boolean {
        val root = rootDir(cfg.id)
        return root.exists() && root.listFiles()?.any { it.isDirectory } == true
    }

    /** 下载 + 解压 rootfs 到实例目录，progress 0..1。 */
    suspend fun install(cfg: RootfsConfig, progress: (Float) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = rootDir(cfg.id)
                if (root.exists()) root.deleteRecursively()
                root.mkdirs()

                val archive = archiveFile(cfg.id)
                archive.parentFile?.mkdirs()
                if (!archive.exists() || archive.length() == 0L) {
                    val request = Request.Builder().url(cfg.url).build()
                    http.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) error("下载失败：HTTP ${resp.code}")
                        val total = resp.body?.contentLength() ?: -1L
                        var done = 0L
                        FileOutputStream(archive).use { out ->
                            resp.body!!.byteStream().use { input ->
                                val buf = ByteArray(64 * 1024)
                                var n = input.read(buf)
                                while (n > 0) {
                                    out.write(buf, 0, n)
                                    done += n
                                    if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 0.6f))
                                    n = input.read(buf)
                                }
                            }
                        }
                    }
                }
                progress(0.75f)
                extractTar(archive, root)
                archive.delete()
                progress(1f)
            }
        }

    /** 解压 tar（支持 gz / xz / bz2）。 */
    private fun extractTar(archive: File, dest: File) {
        fun openStream(): java.io.InputStream {
            val raw = FileInputStream(archive)
            return when (archive.name.substringAfterLast('.', "")) {
                "gz" -> GZIPInputStream(raw, 64 * 1024)
                "xz" -> XZCompressorInputStream(raw)
                "bz2" -> BZip2CompressorInputStream(raw)
                else -> error("不支持的压缩格式：${archive.name}（请改用 tar.gz / tar.xz）")
            }
        }
        openStream().use { raw ->
            TarArchiveInputStream(raw).use { tar ->
                var entry = tar.nextTarEntry
                while (entry != null) {
                    if (entry.isDirectory) {
                        File(dest, entry.name.removeSuffix("/")).mkdirs()
                    } else {
                        val target = File(dest, entry.name)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> tar.copyTo(out, 64 * 1024) }
                        val mode = entry.mode
                        if (mode != 0) {
                            target.setExecutable(mode and 64 != 0, false)
                            if (mode and 128 != 0) target.setExecutable(true, false)
                            target.setReadable(mode and 256 != 0, false)
                        }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
    }

    /** 删除实例（目录 + 数据）。 */
    suspend fun uninstall(cfg: RootfsConfig) = withContext(Dispatchers.IO) {
        runCatching {
            rootDir(cfg.id).deleteRecursively()
            licodeHome(cfg.id).deleteRecursively()
            archiveFile(cfg.id).delete()
        }
    }
}