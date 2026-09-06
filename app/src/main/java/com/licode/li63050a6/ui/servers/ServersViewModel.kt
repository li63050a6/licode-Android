package com.licode.li63050a6.ui.servers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.licode.li63050a6.LicodeApp
import com.licode.li63050a6.data.LicodeClient
import com.licode.li63050a6.domain.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 连接一台服务器的结果。 */
enum class ConnectResult {
    已就绪,     // 在线且已登录（或无需密码）
    需要登录,   // 在线但未登录
    离线,       // 无法连通
}

/** 服务器列表页状态。 */
data class ServersUiState(
    val servers: List<ServerConfig> = emptyList(),
    val probingId: String? = null,
    val connectingId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class ServersViewModel(app: Application) : AndroidViewModel(app) {
    private val container get() = (getApplication<LicodeApp>()).container
    private val store get() = container.serverStore
    private val http get() = container.http

    private val _state = MutableStateFlow(ServersUiState())
    val state: StateFlow<ServersUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(servers = store.servers())
    }

    /** 新增服务器。 */
    fun addServer(cfg: ServerConfig) {
        store.add(cfg)
        load()
    }

    /** 更新服务器。 */
    fun updateServer(cfg: ServerConfig) {
        store.update(cfg)
        load()
    }

    /** 删除服务器。 */
    fun deleteServer(id: String) {
        store.remove(id)
        load()
    }

    /** 探测在线状态 + 是否启用登录。 */
    fun probe(cfg: ServerConfig) {
        viewModelScope.launch {
            _state.value = _state.value.copy(probingId = cfg.id)
            val ok = LicodeClient(http, cfg).healthOk()
            val message = when {
                ok -> "${cfg.name} 在线"
                else -> "${cfg.name} 无法连接，请检查地址与网络"
            }
            _state.value = _state.value.copy(probingId = null, message = message)
        }
    }

    /**
     * 尝试连接一台服务器：
     * 已登录/免登录 → 已就绪并设为当前；
     * 配置了用户名密码 → 用其自动登录；
     * 未登录且无凭据 → 需要登录；连不上 → 离线。
     */
    fun connect(cfg: ServerConfig, onResult: (ConnectResult) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(connectingId = cfg.id, busy = true)
            val result = withContext(Dispatchers.IO) {
                val client = LicodeClient(http, cfg)
                if (!client.healthOk()) return@withContext ConnectResult.离线
                val auth = client.authInfo()
                if (auth?.enabled == true) {
                    when {
                        client.version() != null -> {
                            store.setCurrent(cfg.id)
                            ConnectResult.已就绪
                        }
                        !cfg.username.isNullOrBlank() && !cfg.password.isNullOrBlank() &&
                            client.login(cfg.username!!, cfg.password!!) -> {
                            store.setCurrent(cfg.id)
                            ConnectResult.已就绪
                        }
                        else -> ConnectResult.需要登录
                    }
                } else {
                    store.setCurrent(cfg.id)
                    ConnectResult.已就绪
                }
            }
            _state.value = _state.value.copy(connectingId = null, busy = false)
            onResult(result)
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}