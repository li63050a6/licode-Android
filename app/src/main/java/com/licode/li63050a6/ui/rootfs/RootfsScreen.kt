package com.licode.li63050a6.ui.rootfs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.licode.li63050a6.domain.RootfsConfig

/** 本机环境页：rootfs 实例管理 + 本机 licode 服务器。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootfsScreen(onBack: () -> Unit) {
    val vm: RootfsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var showWebHint by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本机环境") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showWebHint = true }) {
                        Icon(Icons.Default.Info, contentDescription = "帮助")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加环境")
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RootBanner(state) }
            item { ServerStatusCard(state, vm, showWebHint) }
            item { Text("Linux 环境（rootfs）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (state.instances.isEmpty()) {
                item { Text("还没有环境，点右下角 + 添加（下载 Linux 根文件系统作为 AI 工作区）", style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.instances, key = { it.cfg.id }) { inst ->
                InstanceCard(inst = inst, vm = vm)
            }
        }
    }

    if (showAdd) {
        AddRootfsDialog(
            onDismiss = { showAdd = false },
            onConfirm = { cfg ->
                vm.add(cfg)
                showAdd = false
            },
        )
    }
    if (showWebHint) {
        WebHelpDialog(onDismiss = { showWebHint = false })
    }
}

/** 顶部：root 权限状态。 */
@Composable
fun RootBanner(state: RootfsUiState) {
    val (text, color) = when {
        state.rootProbing -> "正在检测 root 权限…" to MaterialTheme.colorScheme.tertiary
        state.rootAvailable -> "已获得 root 权限（本机服务器可运行）" to MaterialTheme.colorScheme.primary
        else -> "未检测到 root：本机服务器需要 Magisk/SuperSU 授权；下载 rootfs 不需 root" to MaterialTheme.colorScheme.error
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.rootProbing) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            } else {
                Text(if (state.rootAvailable) "✓" else "✗", fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

/** 本机 licode 服务器状态。 */
@Composable
fun ServerStatusCard(state: RootfsUiState, vm: RootfsViewModel, showWebHint: Boolean) {
    val active = state.instances.firstOrNull { it.isActive }
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("本机 licode 服务器（手机上直接运行，Web 界面可远程访问）", style = MaterialTheme.typography.titleSmall)
            if (active == null) {
                Text("选择一个环境点「启动本机服务器」即可在本机运行 licode。", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("当前工作区：${active.cfg.name}（${active.cfg.os}）", style = MaterialTheme.typography.bodyMedium)
                val running = active.licodeRunning
                Text(
                    if (running) "状态：运行中" else "状态：已停止",
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                Text("本机访问：http://127.0.0.1:${active.cfg.licodePort}", style = MaterialTheme.typography.bodySmall)
                state.lanAddresses.forEach { ip ->
                    Text("局域网访问：http://$ip:${active.cfg.licodePort}", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.openWeb(active.cfg) }, enabled = running) {
                        Text("打开 Web 界面")
                    }
                    OutlinedButton(onClick = { vm.stopLicode(active.cfg) }, enabled = running) {
                        Text("停止")
                    }
                }
            }
            if (state.log.isNotBlank()) {
                Text("日志（尾）：\n${state.log.takeLast(400)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 单个 rootfs 实例卡片。 */
@Composable
fun InstanceCard(inst: RootfsInstance, vm: RootfsViewModel) {
    val cfg = inst.cfg
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cfg.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (cfg.os.isNotBlank()) Text(cfg.os, style = MaterialTheme.typography.bodySmall)
                }
                if (inst.isActive) {
                    Text("当前工作区", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text("端口：licode ${cfg.licodePort}", style = MaterialTheme.typography.bodySmall)
            if (cfg.sshHost?.isNotBlank() == true) {
                Text(
                    "SSH 远程工作区：${cfg.sshUser ?: "root"}@${cfg.sshHost}:${cfg.sshPort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                if (inst.installed) "已安装" else "未安装",
                color = if (inst.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
            if (inst.busy && inst.progress > 0f && inst.progress < 1f) {
                LinearProgressIndicator(progress = { inst.progress }, modifier = Modifier.fillMaxWidth())
            }
            if (inst.busy && inst.progress == 0f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("处理中…", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!inst.installed) {
                    OutlinedButton(onClick = { vm.install(cfg) }, enabled = !inst.busy && cfg.url.isNotBlank()) {
                        Text("下载安装")
                    }
                } else {
                    OutlinedButton(
                        onClick = { if (inst.licodeRunning) vm.stopLicode(cfg) else vm.startLicode(cfg) },
                        enabled = !inst.busy,
                    ) { Text(if (inst.licodeRunning) "停止本机服务器" else "启动本机服务器") }
                }
                IconButton(onClick = { vm.remove(cfg) }, enabled = !inst.busy) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

/** 添加 rootfs 对话框。 */
@Composable
fun AddRootfsDialog(onDismiss: () -> Unit, onConfirm: (RootfsConfig) -> Unit) {
    var name by remember { mutableStateOf("") }
    var os by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var ws by remember { mutableStateOf("root/workspace") }
    var lport by remember { mutableStateOf("8080") }
    var lpwd by remember { mutableStateOf("") }
    var sshHost by remember { mutableStateOf("") }
    var sshUser by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 Linux 环境（rootfs）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称（必填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = os, onValueChange = { os = it }, label = { Text("系统说明，如 Debian 12") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("下载地址（tar.gz / tar.xz）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ws, onValueChange = { ws = it }, label = { Text("工作区相对路径") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lport, onValueChange = { lport = it }, label = { Text("licode 端口") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lpwd, onValueChange = { lpwd = it }, label = { Text("Web 界面密码（可选）") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("SSH 远程工作区（可选）：让 AI 通过 ssh 连接别的服务器", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(value = sshHost, onValueChange = { sshHost = it }, label = { Text("远程主机地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sshUser, onValueChange = { sshUser = it }, label = { Text("远程用户名（默认 root）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, label = { Text("远程 SSH 端口") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sshKey, onValueChange = { sshKey = it }, label = { Text("私钥内容（PEM，整段粘贴）") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    onConfirm(
                        RootfsConfig(
                            name = name.trim(),
                            os = os.trim(),
                            url = url.trim(),
                            workspaceSub = ws.ifBlank { "root/workspace" },
                            licodePort = lport.toIntOrNull() ?: 8080,
                            licodePassword = lpwd.ifBlank { null },
                            sshHost = sshHost.ifBlank { null },
                            sshUser = sshUser.ifBlank { null },
                            sshPort = sshPort.toIntOrNull() ?: 22,
                            sshKey = sshKey.ifBlank { null },
                        )
                    )
                },
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 帮助说明。 */
@Composable
fun WebHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用说明") },
        text = {
            Text(
                "· licode 服务器直接运行在手机上（root 权限）。\n" +
                    "· 它的工作目录是所选 rootfs 环境的某个目录，AI 直接编辑/运行其中的文件。\n" +
                    "· 手机和同局域网设备用浏览器打开 Web 界面即可远程使用。\n" +
                    "· 配置 SSH 远程工作区后，会把私钥写入该环境的 ~/.ssh，AI 可 ssh 连接别的服务器并把远端当作工作区。\n" +
                    "· 下载 rootfs 不需要 root；默认配置在 Web 界面「设置」里填 API 密钥。",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}