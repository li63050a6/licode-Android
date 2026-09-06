package com.licode.li63050a6.ui.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.licode.li63050a6.domain.ServerConfig
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    onEnterChat: () -> Unit,
    onNeedLogin: (String) -> Unit,
    onLocalServer: () -> Unit,
) {
    val vm: ServersViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showEdit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServerConfig?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("licode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("AI 编程助手 · 手机客户端", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = onLocalServer) {
                        Icon(Icons.Default.Settings, contentDescription = "本机环境")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEdit = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加服务器")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.servers.isEmpty()) {
                EmptyServers(onAdd = { editing = null; showEdit = true })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.servers, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            probing = state.probingId == server.id,
                            connecting = state.connectingId == server.id,
                            onConnect = {
                                vm.connect(server) { result ->
                                    when (result) {
                                        ConnectResult.已就绪 -> onEnterChat()
                                        ConnectResult.需要登录 -> onNeedLogin(server.id)
                                        ConnectResult.离线 -> vm.probe(server)
                                    }
                                }
                            },
                            onEdit = { editing = it; showEdit = true },
                            onDelete = { vm.deleteServer(it.id) },
                        )
                    }
                }
            }
        }
    }

    if (showEdit) {
        ServerEditDialog(
            server = editing,
            onDismiss = { showEdit = false },
            onSave = { cfg ->
                if (editing == null) vm.addServer(cfg) else vm.updateServer(cfg)
                showEdit = false
            },
        )
    }
}

@Composable
private fun EmptyServers(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("还没有服务器", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "点击右下角按钮添加一台运行 licode 的服务器，\n即可远程连接使用。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onAdd) { Text("添加服务器") }
    }
}

@Composable
private fun ServerCard(
    server: ServerConfig,
    probing: Boolean,
    connecting: Boolean,
    onConnect: () -> Unit,
    onEdit: (ServerConfig) -> Unit,
    onDelete: (ServerConfig) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onConnect() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(
                    Modifier.size(width = 48.dp, height = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        server.name.take(1).ifBlank { "S" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${server.scheme}://${server.host}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (probing || connecting) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(if (connecting) "连接中…" else "探测中…", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            when {
                                !server.password.isNullOrBlank() -> "凭据已设置"
                                server.username?.isNotBlank() == true -> "需登录"
                                else -> "免登录"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            menuOpen = false
                            onEdit(server)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = {
                            menuOpen = false
                            onDelete(server)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerEditDialog(
    server: ServerConfig?,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit,
) {
    var name by remember { mutableStateOf(server?.name ?: "") }
    var host by remember { mutableStateOf(server?.host ?: "") }
    var scheme by remember { mutableStateOf(server?.scheme ?: "http") }
    var username by remember { mutableStateOf(server?.username ?: "") }
    var password by remember { mutableStateOf(server?.password ?: "") }
    var trustAll by remember { mutableStateOf(server?.trustAllCerts ?: false) }
    val id = server?.id ?: UUID.randomUUID().toString()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (server == null) "添加服务器" else "编辑服务器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（如：我的服务器）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("地址 host:port（如 192.168.1.10:8080）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("协议：" + if (host.startsWith("https")) "https" else scheme)
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { scheme = if (scheme == "http") "https" else "http" }) {
                        Text(scheme)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("信任自签名证书", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = trustAll, onCheckedChange = { trustAll = it })
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名（默认 licode，可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（服务器启用登录时填写）") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.isNotBlank(),
                onClick = {
                    onSave(
                        ServerConfig(
                            id = id,
                            name = name.ifBlank { host },
                            host = host.trim().replaceFirst("^[a-zA-Z]+://".toRegex(), ""),
                            scheme = scheme,
                            trustAllCerts = trustAll,
                            username = username.ifBlank { null },
                            password = password.ifBlank { null },
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}