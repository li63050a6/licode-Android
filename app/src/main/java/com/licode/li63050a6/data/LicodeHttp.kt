package com.licode.li63050a6.data

import android.content.SharedPreferences
import com.licode.li63050a6.domain.ServerConfig
import kotlinx.serialization.encodeToString
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * OkHttp 客户端工厂：持久化 CookieJar（licode_auth 登录态自动续用）+ 可选自签证书信任。
 * minSdk 21 兼容：不用 android.webkit.CookieManager，自实现简单持久化 CookieJar。
 */
class LicodeHttp(private val prefs: SharedPreferences) {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun client(server: ServerConfig): OkHttpClient = build(server, trustAll = server.trustAllCerts)

    fun clientTrustAll(server: ServerConfig): OkHttpClient = build(server, trustAll = true)

    /** 无服务器限定的普通客户端（rootfs 下载、本机健康探测）。 */
    fun plain(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun build(server: ServerConfig, trustAll: Boolean): OkHttpClient {
        val b = OkHttpClient.Builder()
            .cookieJar(PersistentCookieJar(prefs))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        if (trustAll) {
            applyTrustAll(b)
        }
        return b.build()
    }

    private fun applyTrustAll(b: OkHttpClient.Builder) {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        try {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(tm), SecureRandom())
            b.sslSocketFactory(ctx.socketFactory, tm)
        } catch (_: Exception) {
        }
    }

    /** 简单持久化 CookieJar：主机名 -> 名称 -> 值。 */
    class PersistentCookieJar(private val prefs: SharedPreferences) : CookieJar {

        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { storeCookie(url.host, it) }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return loadMap().getOrDefault(url.host, emptyMap())
                .mapNotNull { (name, value) ->
                    runCatching {
                        Cookie.Builder()
                            .name(name).value(value)
                            .domain(url.host).path("/")
                            .expiresAt(System.currentTimeMillis() + 30L * 24 * 3600 * 1000)
                            .build()
                    }.getOrNull()
                }
        }

        fun hasSessionCookieFor(host: String): Boolean {
            return loadMap().getOrDefault(host, emptyMap()).isNotEmpty()
        }

        private fun storeCookie(host: String, cookie: Cookie) {
            val map = loadMap().toMutableMap()
            val vals = map.getOrDefault(host, mutableMapOf()).toMutableMap()
            vals[cookie.name] = cookie.value
            map[host] = vals
            persist(map)
        }

        private fun loadMap(): Map<String, Map<String, String>> {
            val raw = prefs.getString(KEY, null) ?: return emptyMap()
            return runCatching {
                json.decodeFromString<Map<String, Map<String, String>>>(raw)
            }.getOrDefault(emptyMap())
        }

        private fun persist(map: Map<String, Map<String, String>>) {
            prefs.edit().putString(KEY, json.encodeToString(map)).apply()
        }

        private companion object {
            const val KEY = "cookies"
        }
    }
}