package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.utils.BackupManager

@Composable
fun BackupDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch { msg = BackupManager.export(ctx, uri) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { msg = BackupManager.restore(ctx, uri) }
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("备份与恢复") },
        text = {
            Column {
                Text("导出：打包全部数据（数据库+设置+文件+工作区）→zip", style = MaterialTheme.typography.bodySmall)
                Text("导入：选择备份zip完整恢复", style = MaterialTheme.typography.bodySmall)
                if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { exportLauncher.launch("backup.zip") }) { Text("导出") } },
        dismissButton = {
            Row { TextButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) { Text("导入") }; Spacer(Modifier.height(0.dp)); TextButton(onClick = onDismiss) { Text("关闭") } }
        }
    )
}
