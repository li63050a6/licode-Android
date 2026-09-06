package com.licode.li63050a6.ui.rootfs

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.licode.li63050a6.LicodeApp
import com.licode.li63050a6.data.Shell
import com.licode.li63050a6.domain.RootfsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一个实例的运行时状态。 */
data class RootfsInstance(
    val cfg: RootfsConfig,
    val installed: Boolean = false,
    val licodeRunning: Boolean = false,
    val busy: Boolean = false,
    val progress: Float = 0f,
    val note: String? = null,
    val isActive: Boolean = false,
)

/** 本机环境页状态。 */
data class RootfsUiState(
    val rootAvailable: Boolean = false,
    val rootProbing: Boolean = true,
    val instances: List<RootfsInstance> = emptyList(),
    val lanAddresses: List<String> = emptyList(),
    val message: String? = null,
    val log: String = "",
)

class RootfsViewModel(app: Application) : AndroidViewModel(app) {
    private val container get() = (getApplication<LicodeApp>()).container
    private val store get() = container.rootfsStore
    private val mgr get() = container.rootfs
    private val server get() = container.licodeServer

    private val _state = MutableStateFlow(RootfsUiState())
    val state: StateFlow<RootfsUiState> = _state

    private var pollJob: Job? = null

    init {
        refresh()
    }

    /** 全量刷新：root 检测、实例状态、局域网地址。 */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(rootProbing = true)
            val root = withContext(Dispatchers.IO) { Shell.hasRoot() }
            val lan = withContext(Dispatchers.IO) {
                if (root) server.lanAddresses() else emptyList()
            }
            _state.value = _state.value.copy(
                rootAvailable = root,
                rootProbing = false,
                lanAddresses = lan,
            )
            reloadInstances()
            startPolling()
        }
    }

    private fun reloadInstances() {
        val activeId = store.activeId()
        _state.value = _state.value.copy(
            instances = store.instances().map { cfg ->
                RootfsInstance(
                    cfg = cfg,
                    installed = mgr.isInstalled(cfg),
                    isActive = cfg.id == activeId,
                )
            }
        )
    }

    /** 每 3 秒轮询：运行状态、rootfs 安装状态。 */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                refreshStatuses()
            }
        }
    }

    private fun refreshStatuses() {
        val root = _state.value.rootAvailable
        val activeId = store.activeId()
        viewModelScope.launch {
            val updated = store.instances().map { cfg ->
                val running = root && server.healthOk(cfg.licodePort)
                RootfsInstance(
                    cfg = cfg,
                    installed = mgr.isInstalled(cfg),
                    licodeRunning = running,
                    isActive = cfg.id == activeId,
                )
            }
            _state.value = _state.value.copy(instances = updated)
        }
    }

    /** 添加新实例（仅保存；工作区目录在启动时创建）。 */
    fun add(cfg: RootfsConfig) {
        store.add(cfg)
        reloadInstances()
    }

    /** 删除实例。 */
    fun remove(cfg: RootfsConfig) {
        viewModelScope.launch {
            setBusy(cfg.id, true)
            if (mgr.isInstalled(cfg)) mgr.uninstall(cfg)
            if (store.activeId() == cfg.id) store.setActiveId(null)
            store.remove(cfg.id)
            setBusy(cfg.id, false)
            reloadInstances()
            _state.value = _state.value.copy(message = "已删除 ${cfg.name}")
        }
    }

    /** 下载并解压 rootfs。 */
    fun install(cfg: RootfsConfig) {
        viewModelScope.launch {
            setBusy(cfg.id, true)
            val result = mgr.install(cfg) { p ->
                updateProgress(cfg.id, p)
            }
            result.onSuccess {
                setBusy(cfg.id, false)
                updateProgress(cfg.id, 1f)
                _state.value = _state.value.copy(message = "${cfg.name} 已安装")
                reloadInstances()
            }.onFailure {
                setBusy(cfg.id, false)
                _state.value = _state.value.copy(message = "安装失败：${it.message}")
                reloadInstances()
            }
        }
    }

    /** 在本机启动 licode，工作区指向该 rootfs。 */
    fun startLicode(cfg: RootfsConfig) {
        viewModelScope.launch {
            if (!_state.value.rootAvailable) {
                _state.value = _state.value.copy(rootProbing = true)
                val root = withContext(Dispatchers.IO) { Shell.hasRoot() }
                _state.value = _state.value.copy(rootAvailable = root, rootProbing = false)
                if (!root) {
                    _state.value = _state.value.copy(message = "需要 root 权限（Magisk/SuperSU），当前未检测到")
                    return@launch
                }
            }
            setBusy(cfg.id, true)
            val (ok, msg) = server.start(cfg)
            setBusy(cfg.id, false)
            if (ok) store.setActiveId(cfg.id)
            _state.value = _state.value.copy(
                message = msg,
                log = server.readLog(cfg),
            )
            reloadInstances()
        }
    }

    /** 停止本机 licode。 */
    fun stopLicode(cfg: RootfsConfig) {
        viewModelScope.launch {
            server.stop(cfg)
            if (store.activeId() == cfg.id) store.setActiveId(null)
            _state.value = _state.value.copy(message = "已停止")
            reloadInstances()
        }
    }

    /** 用浏览器打开本机 Web 界面。 */
    fun openWeb(cfg: RootfsConfig) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:${cfg.licodePort}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<LicodeApp>().startActivity(intent)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun setBusy(id: String, v: Boolean) {
        _state.value = _state.value.copy(
            instances = _state.value.instances.map {
                if (it.cfg.id == id) it.copy(busy = v) else it
            }
        )
    }

    private fun updateProgress(id: String, p: Float) {
        _state.value = _state.value.copy(
            instances = _state.value.instances.map {
                if (it.cfg.id == id) it.copy(progress = p, busy = true) else it
            }
        )
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}