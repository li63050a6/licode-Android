package com.licode.li63050a6.data

import com.licode.li63050a6.domain.ClientMessage
import com.licode.li63050a6.domain.ServerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** WebSocket 连接状态。 */
enum class WsState { 连接中, 已连接, 已断开 }

/**
 * licode WebSocket 封装：自动重连（指数退避）+ 心跳 ping + 事件分发。
 * 与服务端 internal/websocket 的 ClientMessage / ServerEvent 协议对齐。
 */
class LicodeWsClient(
    private val scope: CoroutineScope,
    private val okHttp: OkHttpClient,
    private val url: String,
) {
    var onEvent: ((ServerEvent) -> Unit)? = null
    var onState: ((WsState) -> Unit)? = null

    private var ws: WebSocket? = null
    private var attempt = 0
    private var closed = false
    private var pingJob: Job? = null

    val isOpen: Boolean get() = ws != null && wsState == WsState.已连接
    private var wsState: WsState = WsState.已断开

    fun connect() {
        closed = false
        attempt = 0
        start()
    }

    private fun start() {
        if (closed || !scope.isActive) return
        wsState = WsState.连接中
        onState?.invoke(WsState.连接中)
        val req = Request.Builder().url(url).build()
        ws = okHttp.newWebSocket(req, listener)
    }

    /** 发送一条协议消息；未连接时静默丢弃（等待重连）。 */
    fun send(msg: ClientMessage): Boolean {
        val s = ws ?: return false
        val text = runCatching { json.encodeToString(msg) }.getOrNull() ?: return false
        return s.send(text)
    }

    fun sendText(type: String) {
        send(ClientMessage(type = type))
    }

    fun close() {
        closed = true
        pingJob?.cancel()
        ws?.close(1000, "客户端关闭")
        ws = null
        wsState = WsState.已断开
        onState?.invoke(WsState.已断开)
    }

    private fun scheduleReconnect() {
        if (closed) return
        val backoff = (1000L shl (attempt.coerceAtMost(4)))   // 1s → 2s → 4s → 8s → 16s
        attempt++
        scope.launch {
            delay(backoff)
            start()
        }
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && !closed) {
                delay(TimeUnit.SECONDS.toMillis(20))
                ws?.send(json.encodeToString(ClientMessage.ping()))
            }
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
            wsState = WsState.已连接
            onState?.invoke(WsState.已连接)
            startPing()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.isBlank()) return
            val evt = runCatching { json.decodeFromString<ServerEvent>(text) }.getOrNull() ?: return
            onEvent?.invoke(evt)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            pingJob?.cancel()
            wsState = WsState.已断开
            onState?.invoke(WsState.已断开)
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            pingJob?.cancel()
            ws = null
            wsState = WsState.已断开
            onState?.invoke(WsState.已断开)
            scheduleReconnect()
        }
    }

    private companion object {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}