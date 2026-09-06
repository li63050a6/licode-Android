package com.licode.li63050a6.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.licode.li63050a6.data.AppSettings
import com.licode.li63050a6.data.VoiceInputController
import com.licode.li63050a6.data.WsState
import com.licode.li63050a6.ui.voice.VoiceOverlay
import com.licode.li63050a6.domain.Attachment
import com.licode.li63050a6.domain.ToolUi
import com.licode.li63050a6.domain.UiMessage
import java.util.Base64

private const val DEFAULT_TITLE = "新对话"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var selectedPicks by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    var sessionMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var inputKey by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf(TextFieldValue("")) }

    var voiceVisible by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        readPickedImage(context, uri)?.let { pick ->
            selectedPicks = selectedPicks + pick
        }
    }

    val voiceController = remember { VoiceInputController(context) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        voiceController.onResult = { text ->
            voiceText = text
            voiceVisible = false
        }
        voiceController.onCancel = { voiceVisible = false }
        voiceController.onError = { voiceError = it }
        onDispose { voiceController.release() }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }
    LaunchedEffect(voiceError) {
        voiceError?.let {
            snackbar.showSnackbar(it)
            voiceError = null
        }
    }

    val voiceState by voiceController.state.collectAsState()
    val voiceLevel by voiceController.level.collectAsState()
    val voiceAvailable = voiceController.isAvailable()

    LaunchedEffect(state.status) {
        state.status?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.clearStatus()
        }
    }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    val currentTitle = state.sessions.firstOrNull { it.id == state.currentId }?.title ?: DEFAULT_TITLE

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Box {
                                TextButton(onClick = { sessionMenu = true }) {
                                    Text(
                                        currentTitle,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                DropdownMenu(expanded = sessionMenu, onDismissRequest = { sessionMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("新建会话") },
                                        leadingIcon = { Icon(Icons.Default.Create, null) },
                                        onClick = { sessionMenu = false; vm.newSession() },
                                    )
                                    state.sessions.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s.title.ifBlank { DEFAULT_TITLE }, maxLines = 1) },
                                            leadingIcon = {
                                                if (s.id == state.currentId) {
                                                    Text("●", color = MaterialTheme.colorScheme.primary)
                                                }
                                            },
                                            onClick = {
                                                sessionMenu = false
                                                if (s.id != state.currentId) vm.switchSession(s.id)
                                            },
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    state.server?.host ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                ConnectionDot(state.connState)
                            }
                        }
                        Box {
                            IconButton(onClick = { moreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                            }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("删除当前会话") },
                                    onClick = {
                                        moreMenu = false
                                        state.currentId?.let { vm.deleteSession(it) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("设置") },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = {
                                        moreMenu = false
                                        onOpenSettings()
                                    },
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.disconnect()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.connState != WsState.已连接) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.connState == WsState.连接中) "正在连接服务器…" else "连接已断开，正在重连…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            if (state.messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(
                            "连接成功，开始对话",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "输入你的问题，或让它读写文件、搜索代码、执行命令。\n风险操作（Shell/写文件等）会请求你确认。",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.messages) { msg ->
                        MessageItem(msg, isStreaming = state.streaming && msg == state.messages.last())
                    }
                }
            }

            // 已选图片预览
            if (selectedPicks.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectedPicks.forEach { pick ->
                        Box {
                            Image(
                                bitmap = pick.bitmap.asImageBitmap(),
                                contentDescription = "附件",
                                modifier = Modifier.size(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.size(18.dp).align(Alignment.TopEnd),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Default.Close, null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ChatInputBar(
                key = inputKey,
                text = text,
                onTextChange = { text = it },
                onSend = { content ->
                    if (content.isNotBlank() || selectedPicks.isNotEmpty()) {
                        val attachments = selectedPicks.map { it.toAttachment() }
                        vm.sendMessage(content, attachments)
                        selectedPicks = emptyList()
                        inputKey++
                    }
                    voiceText = ""
                },
                onPickImage = { picker.launch("image/*") },
                onStop = { vm.interrupt() },
                streaming = state.streaming,
                voiceAvailable = voiceAvailable,
                onStartVoice = { voiceVisible = true; voiceController.start() },
                onStopVoice = { voiceController.finish(cancel = false) },
                voiceText = voiceText,
            )
        }
    }

    if (voiceVisible) {
        VoiceOverlay(state = voiceState, level = voiceLevel, onCancel = { voiceController.finish(cancel = true) })
    }

    state.pendingAsk?.let { ask ->
        AskDialog(
            ask = ask,
            onApprove = { vm.approveAsk(false) },
            onAlways = { vm.approveAsk(true) },
            onDeny = { vm.denyAsk() },
        )
    }
}

@Composable
private fun ConnectionDot(state: WsState) {
    val color = when (state) {
        WsState.已连接 -> Color(0xFF34C759)
        WsState.连接中 -> Color(0xFFFFB340)
        WsState.已断开 -> Color(0xFFFF3B30)
    }
    Surface(shape = MaterialTheme.shapes.extraSmall, color = color, modifier = Modifier.size(8.dp)) {}
}

@Composable
private fun ChatInputBar(
    key: Int,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit,
    onPickImage: () -> Unit,
    onStop: () -> Unit,
    streaming: Boolean,
    voiceAvailable: Boolean,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    voiceText: String,
) {
    // 语音识别结果回写输入框
    LaunchedEffect(voiceText) {
        if (voiceText.isNotBlank()) {
            val newText = (text.text + voiceText).trimStart()
            onTextChange(TextFieldValue(newText, TextRange(newText.length)))
        }
    }

    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = onPickImage) {
                Icon(Icons.Default.Add, contentDescription = "添加图片")
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f).pointerInput(voiceAvailable) {
                    if (!voiceAvailable) return@pointerInput
                    detectTapGestures(
                        onLongPress = { onStartVoice() },
                    )
                },
                placeholder = { Text("输入消息…") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (streaming) {
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "停止生成",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                IconButton(onClick = { onSend(text.text) }) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "发送",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(10.dp).rotate(180f),
                        )
                    }
                }
            }
        }
    }
}

// ---------------- 消息渲染 ----------------

@Composable
private fun MessageItem(msg: UiMessage, isStreaming: Boolean) {
    when (msg.role) {
        "user" -> UserBubble(msg)
        else -> AssistantBlock(msg, isStreaming)
    }
}

@Composable
private fun UserBubble(msg: UiMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(12.dp)) {
                msg.attachments?.forEach { a ->
                    if (a.type == "image" && a.data.isNotBlank()) {
                        val bmp = remember(a.data) {
                            runCatching {
                                Base64.getDecoder().decode(a.data)
                            }.getOrNull()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        }
                        bmp?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = a.filename,
                                modifier = Modifier.fillMaxWidth()
                                    .height(160.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
                if (msg.content.isNotBlank()) {
                    Text(
                        msg.content,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantBlock(msg: UiMessage, isStreaming: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        msg.tools.forEach { tool -> ToolCard(tool) }
        if (msg.content.isNotBlank() || isStreaming) {
            Text(
                text = Markdown.render(
                    msg.content.ifBlank { "思考中…" },
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.primary,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ToolCard(tool: ToolUi) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tool.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else if (tool.output != null) "查看结果" else "查看参数")
                }
            }
            if (expanded) {
                Text(
                    text = if (tool.output != null) tool.output else tool.args,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---------------- 图片附件 ----------------

private data class PickedImage(
    val bitmap: android.graphics.Bitmap,
    val dataBase64: String,
    val mimeType: String,
    val filename: String,
) {
    fun toAttachment() = Attachment(
        type = "image",
        mimeType = mimeType,
        data = dataBase64,
        filename = filename,
    )
}

private fun readPickedImage(context: android.content.Context, uri: Uri): PickedImage? {
    return runCatching {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val filename = resolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && !c.isNull(idx)) c.getString(idx) else null
        } ?: "图片"
        PickedImage(
            bitmap = bmp,
            dataBase64 = Base64.getEncoder().encodeToString(bytes),
            mimeType = resolver.getType(uri) ?: "image/*",
            filename = filename,
        )
    }.getOrNull()
}