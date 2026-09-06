package com.licode.li63050a6.domain

import kotlinx.serialization.Serializable
import java.util.UUID

/** 一台 licode 服务器配置。 */
@Serializable
data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",                // host:port 或 ip:port
    val scheme: String = "http",          // "http" | "https"
    val trustAllCerts: Boolean = false,   // 信任自签名证书
    val username: String? = null,         // 用户名（默认 licode）
    val password: String? = null,         // 密码（服务器启用登录时填）
) {
    val baseUrl: String
        get() = "$scheme://$host"

    val wsUrl: String
        get() = baseUrl.replaceFirst("http".toRegex(), "ws") + "/ws"

    fun display(): String = "$name  ($baseUrl)"
}