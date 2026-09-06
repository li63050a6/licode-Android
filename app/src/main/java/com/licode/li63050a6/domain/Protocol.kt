package com.licode.li63050a6.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 多模态附件，与后端 ai.Attachment 对齐（snake_case 字段）。 */
@Serializable
data class Attachment(
    val type: String = "image",          // "image" | "file"
    @SerialName("mime_type") val mimeType: String = "",
    val data: String = "",               // base64
    val filename: String? = null,
)

/** FunctionCall / ToolCall，与后端 ai.ToolCall 对齐。 */
@Serializable
data class FunctionCall(
    val name: String = "",
    val arguments: String = "",
)

@Serializable
data class ToolCall(
    val id: String = "",
    val type: String = "function",
    val function: FunctionCall = FunctionCall(),
)

/** 会话历史消息，与后端 ai.Message 对齐。 */
@Serializable
data class Message(
    val role: String = "",
    val content: String = "",
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_name") val toolName: String? = null,
    val attachments: List<Attachment>? = null,
)

/** 会话摘要，与后端 session.Info 对齐。 */
@Serializable
data class SessionInfo(
    val id: String = "",
    val title: String = "",
    val count: Int = 0,
)

/** 客户端 -> 服务端 WebSocket 消息，与后端 websocket.ClientMessage 对齐。 */
@Serializable
data class ClientMessage(
    val type: String,
    val content: String? = null,
    val system: String? = null,
    val settings: JsonElement? = null,
    val askId: String? = null,
    val askApprove: Boolean? = null,
    val askAlways: Boolean? = null,
    val sessionId: String? = null,
    val index: Int? = null,
    val attachments: List<Attachment>? = null,
) {
    companion object {
        fun message(content: String, attachments: List<Attachment>? = null) =
            ClientMessage(type = "message", content = content, attachments = attachments)

        fun ping() = ClientMessage(type = "ping")
        fun interrupt() = ClientMessage(type = "interrupt")

        fun askReply(askId: String, approve: Boolean, always: Boolean) =
            ClientMessage(type = "ask_reply", askId = askId, askApprove = approve, askAlways = always)

        fun sessionsGet() = ClientMessage(type = "sessions_get")
        fun sessionNew() = ClientMessage(type = "session_new")
        fun sessionSwitch(id: String) = ClientMessage(type = "session_switch", sessionId = id)
        fun sessionRename(id: String, title: String) =
            ClientMessage(type = "session_rename", sessionId = id, content = title)
        fun sessionDelete(id: String) = ClientMessage(type = "session_delete", sessionId = id)
        fun sessionBranch(id: String, index: Int) =
            ClientMessage(type = "session_branch", sessionId = id, index = index)
        fun sessionHistory(id: String) = ClientMessage(type = "session_history", sessionId = id)
    }
}

/** 服务端 -> 客户端 WebSocket 事件，与后端 websocket.ServerEvent 对齐。 */
@Serializable
data class ServerEvent(
    val type: String = "",
    val content: String? = null,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolOut: String? = null,
    val error: String? = null,
    val settings: JsonElement? = null,
    val sessions: List<SessionInfo>? = null,
    val sessionId: String? = null,
    val stats: JsonElement? = null,
    val messages: List<Message>? = null,
    val askId: String? = null,
)

/** 待批准的工具调用（ask 事件）。 */
data class AskRequest(
    val askId: String,
    val toolName: String,
    val toolArgs: String,
)

/** UI 消息，简化历史/流式渲染模型。 */
data class UiMessage(
    val role: String,                          // "user" | "assistant"
    val content: String = "",
    val attachments: List<Attachment>? = null,
    val tools: MutableList<ToolUi> = mutableListOf(),
)

/** 工具调用卡片 UI 模型。 */
data class ToolUi(
    val name: String,
    val args: String,
    val output: String? = null,
)