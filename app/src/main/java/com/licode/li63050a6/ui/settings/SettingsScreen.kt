package com.licode.li63050a6.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.licode.li63050a6.data.AppSettings

/** 设置页：背景色、背景图、权限管理。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    val context = LocalContext.current

    // 权限状态（进入时检测一次）
    var storageGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        )
    }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onSettingsChanged(settings.copy(backgroundImageUri = it.toString()))
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        storageGranted = ok
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        micGranted = ok
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("背景", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("背景色", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        ColorPickerGrid(
                            selected = settings.backgroundColor,
                            onSelect = { hex -> onSettingsChanged(settings.copy(backgroundColor = hex, backgroundImageUri = null)) },
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            onSettingsChanged(settings.copy(backgroundColor = null, backgroundImageUri = null))
                        }) { Text("恢复默认") }
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("背景图（2D 图片）", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        if (settings.backgroundImageUri != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                                Spacer(Modifier.width(12.dp))
                                Text("已设置背景图", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                                Text(if (settings.backgroundImageUri == null) "选择图片" else "更换图片")
                            }
                            if (settings.backgroundImageUri != null) {
                                OutlinedButton(onClick = { onSettingsChanged(settings.copy(backgroundImageUri = null)) }) { Text("移除") }
                            }
                        }
                    }
                }
            }

            item { Text("权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PermissionRow(
                            title = "存储权限（读取图片）",
                            granted = storageGranted,
                            onRequest = { storageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) },
                        )
                        PermissionRow(
                            title = "麦克风权限（语音输入）",
                            granted = micGranted,
                            onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        )
                    }
                }
            }

            item { Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("licode Android", style = MaterialTheme.typography.bodyMedium)
                        Text("版本 0.0.0.1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(title: String, granted: Boolean, onRequest: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (granted) "已授权" else "未授权",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!granted) {
            OutlinedButton(onClick = onRequest) { Text("申请") }
        } else {
            Icon(Icons.Default.Check, contentDescription = "已授权", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ColorPickerGrid(selected: String?, onSelect: (String?) -> Unit) {
    val colors = listOf(
        null to "默认",
        "#FFFAFBFF" to "浅蓝白",
        "#FFFFFFFF" to "纯白",
        "#FF12131A" to "深色",
        "#FF4D6BFE" to "品牌蓝",
        "#FF2E7D32" to "墨绿",
        "#FFD84315" to "橙",
        "#FF6A1B9A" to "紫",
        "#FFF48FB1" to "粉",
    )
    val rows = colors.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (hex, label) ->
                    val isSelected = (hex == null && selected == null) || (hex != null && hex == selected)
                    val bg = hex?.let { parseHex(it) } ?: MaterialTheme.colorScheme.surfaceVariant
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { onSelect(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = label, tint = Color.White)
                    }
                }
                // 补齐空白使布局均匀
                repeat(3 - row.size) { Spacer(Modifier.size(48.dp)) }
            }
        }
    }
}

fun parseHex(hex: String): Color = try {
    val clean = hex.removePrefix("#")
    val long = clean.toLong(16)
    when (clean.length) {
        6 -> Color(0xFF000000 or long)
        8 -> Color(long)
        else -> Color.White
    }
} catch (e: Exception) {
    Color.White
}