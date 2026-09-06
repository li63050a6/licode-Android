package com.licode.li63050a6.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.licode.li63050a6.LicodeApp
import com.licode.li63050a6.data.LicodeClient
import com.licode.li63050a6.data.LicodeWsClient
import com.licode.li63050a6.data.WsState
import com.licode.li63050a6.domain.AskRequest
import com.licode.li63050a6.domain.Attachment
import com.licode.li63050a6.domain.ClientMessage
import com.licode.li63050a6.domain.Message
import com.licode.li63050a6.domain.ServerConfig
import com.licode.li63050a6.domain.SessionInfo
import com.licode.li63050a6.domain.ServerEvent
import com.licode.li63050a6.domain.ToolUi
import com.licode.li63050a6.domain.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 对话页状态。 */
data class ChatUiState(
    val server: ServerConfig? = null,
    val connState: WsState = WsState.已断开,
    val sessions: List<SessionInfo> = emptyList(),
    val currentId: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val streaming: Boolean = false,
    val pendingAsk: AskRequest? = null,
    val status: String? = null,
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val container get() = (getApplication<LicodeApp>()).container
    private val store get() = container.serverStore
    private val http get() = container.http

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    private var ws: LicodeWsClient? = null
    private var historyRequested: String? = null

    init {
        val srv = store.current()
        if (srv == null) {
            _state.value = _state.value.copy(error = "未选择服务器")
        } else {
            _state.value = _state.value.copy(server = srv)

            ws = LicodeWsClient(
                scope = viewModelScope,
                okHttp = http.client(srv),
                url = srv.wsUrl,
            ).also {
                it.onEvent = ::handleEvent
                it.onState = { s ->
                    _state.value = _state.value.copy(connState = s)
                    if (s == WsState.已连接) {
                        // 重连后重新拉取会话列表与历史
                        historyRequested = null
                        it.send(ClientMessage.sessionsGet())
                    }
                }
                it.connect()
            }
        }
    }

    // ---------------- 会话操作 ----------------

    fun newSession() {
        messagesToState(emptyList())
        ws?.send(ClientMessage.sessionNew())
    }

    fun switchSession(id: String) {
        if (id == _state.value.currentId) return
        messagesToState(emptyList())
        historyRequested = id
        ws?.send(ClientMessage.sessionSwitch(id))
        ws?.send(ClientMessage.sessionHistory(id))
    }

    fun deleteSession(id: String) {
        ws?.send(ClientMessage.sessionDelete(id))
    }

    fun renameSession(id: String, title: String) {
        if (title.isBlank()) return
        ws?.send(ClientMessage.sessionRename(id, title))
    }

    // ---------------- 发送 / 中断 ----------------

    fun sendMessage(content: String, attachments: List<Attachment> = emptyList()) {
        val text = content.trim()
        if (text.isEmpty() && attachments.isEmpty()) return
        if (_state.value.streaming) {
            _state.value = _state.value.copy(error = "上一条消息仍在处理中")
            return
        }
        if (_state.value.currentId == null) ws?.send(ClientMessage.sessionsGet())
        val list = _state.value.messages.toMutableList()
        list.add(UiMessage(role = "user", content = text, attachments = attachments))
        _state.value = _state.value.copy(messages = list, streaming = true, error = null)
        ws?.send(ClientMessage.message(text, attachments))
    }

    fun interrupt() {
        if (!_state.value.streaming) return
        ws?.send(ClientMessage.interrupt())
        _state.value = _state.value.copy(status = "已请求停止")
    }

    // ---------------- Ask 审批 ----------------

    fun approveAsk(always: Boolean) {
        val ask = _state.value.pendingAsk ?: return
        ws?.send(ClientMessage.askReply(ask.askId, true, always))
        clearAsk()
    }

    fun denyAsk() {
        val ask = _state.value.pendingAsk ?: return
        ws?.send(ClientMessage.askReply(ask.askId, false, false))
        clearAsk()
    }

    private fun clearAsk() {
        _state.value = _state.value.copy(pendingAsk = null)
    }

    // ---------------- 事件处理 ----------------

    private fun handleEvent(ev: ServerEvent) {
        val s = _state.value
        when (ev.type) {
            "sessions" -> {
                _state.value = s.copy(
                    sessions = ev.sessions.orEmpty(),
                    currentId = ev.sessionId,
                )
                val target = ev.sessionId
                if (target != null && (historyRequested == null || historyRequested == target) && s.messages.isEmpty()) {
                    historyRequested = target
                    ws?.send(ClientMessage.sessionHistory(target))
                }
            }

            "history" -> {
                if (ev.sessionId != null && historyRequested == ev.sessionId) {
                    historyRequested = null
                    messagesToState(buildHistory(ev.messages.orEmpty()))
                }
            }

            "delta" -> appendDelta(ev.content.orEmpty())

            "tool_start" -> {
                val name = ev.toolName ?: return
                addTool(ToolUi(name = name, args = ev.toolArgs.orEmpty()))
            }

            "tool_done" -> {
                val name = ev.toolName ?: return
                setToolOut(name, ev.toolOut.orEmpty())
            }

            "done" -> _state.value = _state.value.copy(streaming = false)

            "error" -> _state.value = _state.value.copy(
                streaming = false,
                error = ev.error ?: "发生错误",
            )

            "status" -> _state.value = _state.value.copy(status = ev.content)

            "ask" -> {
                val id = ev.askId ?: return
                _state.value = s.copy(
                    pendingAsk = AskRequest(id, ev.toolName.orEmpty(), ev.toolArgs.orEmpty()),
                )
            }
        }
    }

    private fun appendDelta(text: String) {
        val list = _state.value.messages.toMutableList()
        val last = list.lastOrNull()
        if (last != null && last.role == "assistant" && _state.value.streaming) {
            list[list.size - 1] = last.copy(content = last.content + text)
        } else {
            list.add(UiMessage(role = "assistant", content = text))
        }
        _state.value = _state.value.copy(messages = list, streaming = true)
    }

    private fun addTool(tool: ToolUi) {
        val list = _state.value.messages.toMutableList()
        val last = list.lastOrNull()
        if (last == null || last.role != "assistant") {
            val m = UiMessage(role = "assistant")
            m.tools.add(tool)
            list.add(m)
        } else {
            val tools = last.tools.toMutableList()
            tools.add(tool)
            list[list.size - 1] = last.copy(tools = tools)
        }
        _state.value = _state.value.copy(messages = list)
    }

    private fun setToolOut(toolName: String, out: String) {
        val list = _state.value.messages.toMutableList()
        val last = list.lastOrNull()
        if (last != null && last.role == "assistant") {
            val idx = last.tools.indexOfLast { it.name == toolName }
            if (idx >= 0) {
                val tools = last.tools.toMutableList()
                tools[idx] = tools[idx].copy(output = out)
                list[list.size - 1] = last.copy(tools = tools)
                _state.value = _state.value.copy(messages = list)
            }
        }
    }

    private fun messagesToState(list: List<UiMessage>) {
        val s = _state.value
        _state.value = s.copy(messages = list, streaming = false)
    }

    /** 把完整历史消息（ai.Message）转成 UI 消息列表（tool 结果合并到所属 assistant）。 */
    private fun buildHistory(msgs: List<Message>): List<UiMessage> {
        val out = mutableListOf<UiMessage>()
        for (m in msgs) {
            when (m.role) {
                "user" -> out.add(UiMessage(role = "user", content = m.content, attachments = m.attachments))
                "assistant" -> {
                    val ui = UiMessage(role = "assistant", content = m.content)
                    m.toolCalls?.forEach { tc ->
                        ui.tools.add(ToolUi(name = tc.function.name, args = tc.function.arguments))
                    }
                    out.add(ui)
                }
                "tool" -> {
                    val last = out.lastOrNull()
                    if (last != null) {
                        val tool = last.tools.lastOrNull()
                        if (tool != null) {
                            val tools = last.tools.toMutableList()
                            tools[tools.size - 1] = tool.copy(output = m.content)
                            out[out.size - 1] = last.copy(tools = tools)
                        }
                    }
                }
            }
        }
        return out
    }

    fun disconnect() {
        ws?.close()
    }

    override fun onCleared() {
        ws?.close()
        super.onCleared()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearStatus() {
        _state.value = _state.value.copy(status = null)
    }
}