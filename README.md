# licode-Android —— AI 编程助手手机客户端

**licode（Go 服务端，Web 版）的原生 Android 客户端**，Kotlin + Jetpack Compose 实现，支持**远程连接**任意一台运行 licode 的服务器（局域网/公网）。思维全部在远端服务器完成，手机只是薄客户端。

> 服务端：https://github.com/li63050a/licode
>
> 包名：`com.licode.li63050a6`

## 功能

- **多服务器管理**：添加/编辑/删除多台 licode 服务器，一键切换；`GET /health` + `GET /api/auth` 探测在线/离线/需登录三态
- **远程连接**：`http(s)://host:port` + `ws(s)://host:port/ws`，任意可访问的服务器均可接入
- **登录认证**：基于 Cookie `licode_auth`（HMAC 签名），登录态持久化自动续用；服务器未设密码时免登录
- **多会话对话**：会话列表 / 新建 / 切换 / 重命名 / 删除，流式渲染（`delta` 事件）
- **工具调用可视化**：`tool_start` 参数折叠卡片 + `tool_done` 结果预览
- **权限审批**：风险工具（Shell/写文件等）触发 `ask` 事件 → 弹窗「拒绝 / 允许 / **始终允许**」（仅当前对话生效）
- **中断生成**：发送 `interrupt`，配合服务端 `busy` 语义
- **多模态附件**：图库选图 → base64 → `attachments`（image 类型），支持与服务端 `ai.Attachment` 互通
- 深蓝主色主题，浅色/深色跟随系统

## 支持范围

| 项 | 值 |
| --- | --- |
| 应用名 | `licode` |
| Android 系统版本 | **5.0（API 21）~ 16（API 36）**，已实测构建通过 |
| minSdk / targetSdk / compileSdk | `21` / `36` / `36` |
| 语言/UI | Kotlin + Jetpack Compose（Material3） |
| 网络 | OkHttp（REST + WebSocket）+ CookieJar |
| 序列化 | kotlinx.serialization |

## 架构

```
app/src/main/java/com/licode/li63050a6/
├── MainActivity.kt          # 单 Activity + Compose 导航
├── di/                      # 手动依赖注入容器 AppContainer
├── domain/                  # 协议模型：ServerConfig / ClientMessage / ServerEvent …
├── data/
│   ├── ServerStore.kt       # 服务器配置持久化（SharedPreferences，JSON 序列化）
│   ├── LicodeHttp.kt        # OkHttp 客户端：CookieJar 持久化 / 自签证书信任
│   ├── LicodeClient.kt      # licode API 封装：登录 / 健康探测 / 版本 / 模型
│   └── LicodeWs.kt          # WebSocket 封装：重连 / 心跳 / 事件分发
├── ui/
│   ├── navigation/          # 路由：Servers → Login → Chat
│   ├── servers/             # 服务器列表页（增删改/探测/选择）
│   ├── login/               # 登录页
│   ├── chat/                # 会话列表 + 对话页（流式/工具卡片/Ask 审批/附件/中断）
│   ├── chat/AskDialog.kt    # ask_reply 审批对话框
│   ├── chat/Markdown.kt     # 轻量 Markdown 渲染（标题/粗斜体/代码块/列表）
│   └── theme/               # 深蓝主题 + 浅/深色
```

## WebSocket 协议（与服务端 `internal/websocket/websocket.go` 对齐）

**客户端消息** `{"type": ...}`：

| type | 载荷 |
| --- | --- |
| `message` | `content`、`system`(可选)、`attachments`(可选) |
| `ping` | — |
| `interrupt` | — |
| `ask_reply` | `askId`、`askApprove`、`askAlways` |
| `sessions_get` / `session_new` / `session_switch` / `session_rename` / `session_delete` / `session_history` | `sessionId` / `content` / `index` |
| `settings_get` / `settings_set` | — / `settings` |

**服务端事件**：

| type | 载荷 | 客户端动作 |
| --- | --- | --- |
| `delta` | `content` | 追加到当前助手消息（流式） |
| `tool_start` | `toolName`、`toolArgs` | 展示工具折叠卡片 |
| `tool_done` | `toolName`、`toolOut` | 卡片填充结果 |
| `done` | — | 结束本轮生成 |
| `error` | `error` | 提示错误 |
| `status` | `content` | 状态提示（如思考中…） |
| `ask` | `askId`、`toolName`、`toolArgs` | 弹批准对话框 |
| `sessions` | `sessions`、`sessionId` | 刷新会话列表 |
| `history` | `sessionId`、`messages` | 渲染完整历史 |
| `settings` | `settings` | 设置快照 |
| `stats` | `stats` | 用量统计 |
| `audit_log` | `content` | 审计摘要 |

## REST API 使用

| 用途 | 接口 |
| --- | --- |
| 登录态探测 | `GET /api/auth`（免认证，返回 `enabled/username`） |
| 登录 | `POST /login`（表单 `username/password` → Cookie） |
| 健康/就绪 | `GET /health`、`GET /ready` |
| 版本 | `GET /api/version` |
| 文件 | `GET/POST /api/file`、`POST /api/mkdir`、`/api/delete`、`/api/upload|download`、`GET/POST /api/workspace` |
| 备份迁移 | `GET /api/export`、`POST /api/import` |

## 环境要求（本地构建已验证）

| 依赖 | 位置 |
| --- | --- |
| Android SDK | `/data/home/admin1/work/apk/android-sdk`（platforms 33/34/35/36、build-tools 36.0.0） |
| Gradle | `/data/home/admin1/work/apk/gradle/gradle-8.9` |
| JDK | `/usr/lib/jvm/java-17-openjdk-amd64`（构建需设 `JAVA_HOME`） |

## 构建

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/data/home/admin1/work/apk/android-sdk
/data/home/admin1/work/apk/gradle/gradle-8.9/bin/gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 里程碑

- [x] **M0** 工程骨架（Gradle/AGP/manifest/minSdk21）
- [x] **M1** 远程连接闭环（服务器列表 + 登录 + CookieJar + 健康探测 + WS 封装重连/心跳/事件流）
- [x] **M2** 核心对话（会话列表 + 对话页：流式 / Markdown / 工具卡片 / Ask 审批 / 中断 / 图片附件）
- [ ] **M3** 文件浏览器（读/写/上传/下载）+ 设置面板 + 多服务器切换验证
- [ ] **M4** 增强（搜索/审计接入、自签证书信任引导、离线会话缓存、凭据本地加密）

> 当前进度：M0 ~ M2 已实现并通过 `gradle :app:assembleDebug` 构建验证（产物 `app/build/outputs/apk/debug/app-debug.apk`，约 10.7 MB，仅声明 INTERNET 权限）。

## 里程碑设计说明

- **认证**：licode 对所有 REST/WS 走 Cookie 校验（`auth.require`），`Basic Auth` 未启用 → 统一走 `POST /login` + 持久化 CookieJar。
- **会话数据在远端**：每台服务器的会话历史存于各自 `~/.licode/sessions/`，切换服务器即切换数据，天然隔离。
- **自签 HTTPS**：licode `--https` 生成自签名证书，Android 默认不信任 → 服务器编辑页提供「信任自签名证书」开关（M4 完善引导）。