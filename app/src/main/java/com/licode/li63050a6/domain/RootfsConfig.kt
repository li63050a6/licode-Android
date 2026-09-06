package com.licode.li63050a6.domain

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * 一个 Linux 根文件系统（rootfs）实例。
 * licode 直接运行在手机上，工作区（进程启动目录）指向该实例内某个目录。
 */
@Serializable
data class RootfsConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",                 // 实例名称
    val os: String = "",                   // 系统描述，如 Ubuntu 24.04
    val url: String = "",                  // 下载地址（tar.gz / tar.xz / tar.zst）
    val workspaceSub: String = "root/workspace", // 工作区在 rootfs 内的相对路径
    val licodePort: Int = 8080,            // licode 服务端口
    val licodePassword: String? = null,    // licode Web 界面登录密码（可选）
    // SSH 远程工作区：让 AI 通过 ssh 登录别的服务器，把远端作为工作区
    val sshHost: String? = null,           // 远程主机地址
    val sshUser: String? = null,           // 远程用户名（默认 root）
    val sshPort: Int = 22,                 // 远程 SSH 端口
    val sshKey: String? = null,            // 私钥内容（PEM）
    val createdAt: Long = System.currentTimeMillis(),
)