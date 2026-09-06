package com.licode.li63050a6.data

import com.licode.li63050a6.domain.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/** GET /api/auth 返回体：描述服务器登录是否启用。 */
@Serializable
data class AuthInfo(
    val enabled: Boolean = false,
    val username: String = "",
    val default_username: String = "licode",
)

/**
 * licode REST 层封装（登录 / 健康探测 / 版本 / 登录态）：
 * 所有认证走 Cookie（licode_auth，HMAC 签名），Basic Auth 未启用。
 */
class LicodeClient(
    private val http: LicodeHttp,
    private val server: ServerConfig,
) {
    private val okHttp by lazy { http.client(server) }

    fun baseUrl(): String = server.baseUrl
    fun wsUrl(): String = server.wsUrl

    /** 服务器是否在线（/health）。 */
    suspend fun healthOk(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            okHttp.newCall(
                Request.Builder().url(server.baseUrl + "/health").build()
            ).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** 服务器登录配置（不需认证即可查询）。 */
    suspend fun authInfo(): AuthInfo? = withContext(Dispatchers.IO) {
        runCatching {
            okHttp.newCall(
                Request.Builder().get().url(server.baseUrl + "/api/auth").build()
            ).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()?.let {
                    runCatching { jsonDecode<AuthInfo>(it) }.getOrNull()
                } else null
            }
        }.getOrNull()
    }

    /** 登录：POST /login 表单，成功后 CookieJar 持有 licode_auth。 */
    suspend fun login(username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = FormBody.Builder()
                    .add("username", username)
                    .add("password", password)
                    .build()
                okHttp.newCall(
                    Request.Builder().url(server.baseUrl + "/login").post(body).build()
                ).execute().use { }
                // 登录成功 → Set-Cookie licode_auth；随后校验登录态
                var u = server.baseUrl.replaceFirst("http://", "").replaceFirst("https://", "")
                val host = u.substringBefore("/").substringBefore(":")
                val jar = (okHttp.cookieJar as? LicodeHttp.PersistentCookieJar)
                jar?.hasSessionCookieFor(host) == true
            }.getOrDefault(false)
        }

    /** 请求一次受保护的 /api/version 校验登录态（未登录返回 null）。 */
    suspend fun version(): String? = withContext(Dispatchers.IO) {
        try {
            okHttp.newCall(
                Request.Builder().get().url(server.baseUrl + "/api/version").build()
            ).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: IOException) {
            null
        }
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private inline fun <reified T> jsonDecode(s: String): T = json.decodeFromString<T>(s)
}