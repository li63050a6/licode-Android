package com.licode.li63050a6.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.licode.li63050a6.domain.AskRequest

/**
 * 工具权限审批对话框：对应服务端 ask 事件。
 * 拒绝 / 允许 / 始终允许（仅当前对话生效，AskAlways=true）。
 */
@Composable
fun AskDialog(
    ask: AskRequest,
    onApprove: () -> Unit,
    onAlways: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text("请求执行工具") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    ask.toolName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (ask.toolArgs.isNotBlank()) {
                    Text(
                        ask.toolArgs,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 10,
                    )
                }
                Text(
                    "该工具需要你的授权后才能继续执行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onApprove) { Text("允许执行") }
                    TextButton(onClick = onAlways) { Text("始终允许") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) { Text("拒绝") }
        },
    )
}