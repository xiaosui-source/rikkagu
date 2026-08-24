/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.rikkahub.data.ai.agents.AgentProfile
import me.rerere.rikkahub.data.ai.agents.AgentStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinInject
import org.koin.androidx.compose.koinViewModel

/**
 * 智能体管理页：配置多个不同专长的智能体（写作/翻译/代码…），
 * 每个绑定一个现有 Assistant。主助手中会注入 agent_call_<id> 工具，
 * 可在对话中把子任务转交给对应智能体处理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSettingsPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsState()
    val agentStore = koinInject<AgentStore>()
    val agents by agentStore.agents.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AgentProfile?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("智能体管理") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CardGroup {
                item(
                    leadingContent = { Icon(HugeIcons.AiMagic, null) },
                    headlineContent = { Text("多智能体联合工作") },
                    supportingContent = {
                        Text(
                            "配置多个不同专长的智能体后，主助手在对话中可自动把子任务转交给对应智能体处理，结果回传后继续回答，无需手动切换助手。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                )
                item(
                    leadingContent = { Icon(HugeIcons.PlusSign, null) },
                    headlineContent = { Text("添加智能体") },
                    supportingContent = {
                        Text(
                            "创建写作助手、翻译助手、代码助手等，每个绑定一个现有助手（模型）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showAdd = true },
                )
            }

            CardGroup(
                title = { Text("智能体列表（${agents.size}）") },
            ) {
                if (agents.isEmpty()) {
                    item {
                        Text(
                            "还没有智能体。点击上方「添加智能体」创建一个。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                agents.forEach { agent ->
                    val assistant = settings.assistants.firstOrNull { it.id == agent.assistantId }
                    item(
                        onClick = { editing = agent },
                        leadingContent = { Icon(HugeIcons.AiMagic, null) },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(agent.name)
                                if (!agent.enabled) {
                                    Text(
                                        "（停用）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            Text(
                                buildString {
                                    if (agent.description.isNotBlank()) append(agent.description).append(" · ")
                                    append("绑定：").append(assistant?.name ?: "未绑定助手")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    scope.launch { agentStore.remove(agent.id) }
                                }) {
                                    Icon(HugeIcons.Delete02, null, tint = MaterialTheme.colorScheme.error)
                                }
                                Switch(
                                    checked = agent.enabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch { agentStore.update(agent.copy(enabled = enabled)) }
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AgentEditDialog(
            title = "添加智能体",
            assistants = settings.assistants,
            initial = null,
            onDismiss = { showAdd = false },
            onConfirm = { profile ->
                scope.launch { agentStore.add(profile) }
                showAdd = false
            }
        )
    }

    editing?.let { agent ->
        AgentEditDialog(
            title = "编辑智能体",
            assistants = settings.assistants,
            initial = agent,
            onDismiss = { editing = null },
            onConfirm = { updated ->
                scope.launch { agentStore.update(updated) }
                editing = null
            }
        )
    }
}

@Composable
private fun AgentEditDialog(
    title: String,
    assistants: List<me.rerere.rikkahub.data.model.Assistant>,
    initial: AgentProfile?,
    onDismiss: () -> Unit,
    onConfirm: (AgentProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(initial?.systemPrompt ?: "") }
    var assistantId by remember {
        mutableStateOf(initial?.assistantId ?: assistants.firstOrNull()?.id ?: me.rerere.rikkahub.data.model.Assistant().id)
    }
    var temperature by remember { mutableStateOf(initial?.temperature?.toString() ?: "") }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（如：写作助手）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("能力描述（模型据此判断何时转交）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("系统提示词（可选，默认用绑定助手的）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text(
                        "绑定助手（决定使用哪个模型/密钥）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    assistants.forEach { a ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = a.id == assistantId,
                                onClick = { assistantId = a.id }
                            )
                            Text(
                                a.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp, end = 8.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it },
                    label = { Text("温度（可选，如 0.7）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用")
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val profile = AgentProfile(
                        id = initial?.id ?: kotlin.uuid.Uuid.random().toString(),
                        name = name.trim().ifBlank { "未命名智能体" },
                        description = description.trim(),
                        assistantId = assistantId,
                        systemPrompt = systemPrompt.trim(),
                        temperature = temperature.trim().toFloatOrNull(),
                        enabled = enabled,
                    )
                    onConfirm(profile)
                },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
