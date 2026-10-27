package com.campfire.wechatassistant.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campfire.wechatassistant.core.Alert
import com.campfire.wechatassistant.core.Llm
import com.campfire.wechatassistant.core.ListText
import com.campfire.wechatassistant.core.Rule
import com.campfire.wechatassistant.core.Scope
import com.campfire.wechatassistant.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Actions(
    val openAccessibility: () -> Unit,
    val requestNotifications: () -> Unit,
    val openWechat: () -> Unit,
)

private enum class Tab(val label: String, val icon: ImageVector) {
    ALERTS("提醒", Icons.Default.Home), RULES("规则", Icons.Default.List), SETTINGS("设置", Icons.Default.Settings)
}

@Composable
fun MainScreen(serviceOn: Boolean, actions: Actions) {
    var tab by rememberSaveable { mutableStateOf(Tab.ALERTS) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.ALERTS -> AlertsTab(serviceOn, actions)
                Tab.RULES -> RulesTab()
                Tab.SETTINGS -> SettingsTab(serviceOn, actions)
            }
        }
    }
}

@Composable
private fun Header(title: String, sub: String) {
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(10.dp).clip(CircleShape).background(color))

private fun ago(ms: Long): String {
    val m = ((System.currentTimeMillis() - ms) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "刚刚"
        m < 60 -> "$m 分钟前"
        m < 1440 -> "${m / 60} 小时前"
        else -> "${m / 1440} 天前"
    }
}

// ---------------- 提醒 ----------------

@Composable
private fun AlertsTab(serviceOn: Boolean, actions: Actions) {
    val alerts by Store.alerts.collectAsState()
    val settings by Store.settings.collectAsState()
    val running = serviceOn && settings.monitoring
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Header("个微消息助手", "只读微信新消息，按规则提醒，回复由你自己发") }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (running) okColor() else warnColor())
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                !serviceOn -> "无障碍服务未开启"
                                !settings.monitoring -> "监听已暂停"
                                else -> "正在读取微信新消息"
                            },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (!serviceOn) "到系统无障碍设置里开启「个微消息助手」" else "只读，不点击、不输入、不发送",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (serviceOn) {
                        Switch(settings.monitoring, { v -> Store.updateSettings { it.copy(monitoring = v) } })
                    } else {
                        Button(onClick = actions.openAccessibility) { Text("去开启") }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("命中规则的消息（${alerts.count { !it.handled }} 条待处理）", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (alerts.isNotEmpty()) TextButton(onClick = { Store.clearAlerts() }) { Text("清空") }
            }
        }
        if (alerts.isEmpty()) {
            item {
                Panel {
                    Text("还没有命中规则的消息", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "开启无障碍服务后，微信里来了新消息（通知或聊天列表上的预览），命中「规则」页里的条件就会出现在这里。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(alerts, key = { it.id }) { AlertCard(it, actions) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AlertCard(a: Alert, actions: Actions) {
    val clipboard = LocalClipboardManager.current
    val settings by Store.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var reply by remember(a.id) { mutableStateOf(a.suggestion) }
    var busy by remember(a.id) { mutableStateOf(false) }
    var err by remember(a.id) { mutableStateOf<String?>(null) }
    var copied by remember(a.id) { mutableStateOf(false) }
    val m = a.message
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (a.handled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (a.handled) 0.dp else 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.ruleNames.joinToString("、"),
                    Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.weight(1f))
                Text(ago(m.timeMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (m.isGroup) "${m.chat} · ${m.sender.ifBlank { "群成员" }}" else m.chat,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            )
            Text(m.text, style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = reply, onValueChange = { reply = it }, Modifier.fillMaxWidth(),
                label = { Text("建议回复（可修改）") }, minLines = 2,
            )
            if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { clipboard.setText(AnnotatedString(reply)); copied = true }, enabled = reply.isNotBlank()) {
                    Text(if (copied) "已复制" else "复制")
                }
                OutlinedButton(onClick = { actions.openWechat() }) { Text("打开微信去回复") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (settings.llmReady) {
                    TextButton(enabled = !busy, onClick = {
                        busy = true; err = null
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Llm.suggest(settings, a) }
                            r.onSuccess { reply = it; copied = false }.onFailure { err = it.message }
                            busy = false
                        }
                    }) { Text(if (busy) "生成中…" else "AI 生成建议") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { Store.markHandled(a.id, !a.handled) }) { Text(if (a.handled) "标为未处理" else "标为已处理") }
            }
        }
    }
}

// ---------------- 规则 ----------------

@Composable
private fun RulesTab() {
    val rules by Store.rules.collectAsState()
    var editing by remember { mutableStateOf<Rule?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Header("规则", "关键词、联系人、群名，满足就提醒") }
                Button(onClick = { editing = Rule(Store.newRuleId(), "") }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("新建")
                }
            }
        }
        items(rules, key = { it.id }) { r ->
            Card(
                onClick = { editing = r }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(r.name.ifBlank { "（未命名）" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(describe(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(r.enabled, { Store.upsertRule(r.copy(enabled = it)) })
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
    editing?.let { r -> RuleDialog(r, rules.any { it.id == r.id }, onDismiss = { editing = null }) }
}

private fun describe(r: Rule): String {
    val parts = ArrayList<String>()
    parts += when (r.scope) { Scope.ALL -> "私聊和群聊"; Scope.GROUP -> "仅群聊"; Scope.PRIVATE -> "仅私聊" }
    if (r.keywords.isNotEmpty()) parts += "关键词：" + ListText.join(r.keywords)
    if (r.requireAtMe) parts += "被 @ 才算"
    if (r.contacts.isNotEmpty()) parts += "联系人：" + ListText.join(r.contacts)
    if (r.groups.isNotEmpty()) parts += "群：" + ListText.join(r.groups)
    return parts.joinToString(" · ")
}

@Composable
private fun RuleDialog(rule: Rule, exists: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(rule.name) }
    var kw by remember { mutableStateOf(ListText.join(rule.keywords)) }
    var ex by remember { mutableStateOf(ListText.join(rule.excludeKeywords)) }
    var contacts by remember { mutableStateOf(ListText.join(rule.contacts)) }
    var groups by remember { mutableStateOf(ListText.join(rule.groups)) }
    var scope by remember { mutableStateOf(rule.scope) }
    var atMe by remember { mutableStateOf(rule.requireAtMe) }
    var tpl by remember { mutableStateOf(rule.template) }
    val noCondition = ListText.split(kw).isEmpty() && ListText.split(contacts).isEmpty() && ListText.split(groups).isEmpty() && !atMe
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "编辑规则" else "新建规则") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("规则名（提醒时显示）") }, singleLine = true)
                OutlinedTextField(kw, { kw = it }, Modifier.fillMaxWidth(), label = { Text("关键词（逗号或顿号分隔，任一命中）") })
                OutlinedTextField(ex, { ex = it }, Modifier.fillMaxWidth(), label = { Text("排除词（出现就不提醒）") })
                OutlinedTextField(contacts, { contacts = it }, Modifier.fillMaxWidth(), label = { Text("联系人（留空不限）") })
                OutlinedTextField(groups, { groups = it }, Modifier.fillMaxWidth(), label = { Text("群名（留空不限）") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(scope == Scope.ALL, { scope = Scope.ALL }, { Text("全部") })
                    FilterChip(scope == Scope.PRIVATE, { scope = Scope.PRIVATE }, { Text("仅私聊") })
                    FilterChip(scope == Scope.GROUP, { scope = Scope.GROUP }, { Text("仅群聊") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("群里被 @ 我才算", Modifier.weight(1f)); Switch(atMe, { atMe = it })
                }
                OutlinedTextField(
                    tpl, { tpl = it }, Modifier.fillMaxWidth(), minLines = 3,
                    label = { Text("话术模板") },
                    supportingText = { Text("可用 {联系人} {群名} {消息} {关键词} {时间} {我的昵称}") },
                )
                if (noCondition) Text("没有设置任何条件的规则不会匹配任何消息", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                Store.upsertRule(rule.copy(
                    name = name.trim(), keywords = ListText.split(kw), excludeKeywords = ListText.split(ex),
                    contacts = ListText.split(contacts), groups = ListText.split(groups), scope = scope, requireAtMe = atMe, template = tpl.trim(),
                ))
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = { Store.deleteRule(rule.id); onDismiss() }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

// ---------------- 设置 ----------------

@Composable
private fun SettingsTab(serviceOn: Boolean, actions: Actions) {
    val s by Store.settings.collectAsState()
    var nick by remember(s.myNickname) { mutableStateOf(s.myNickname) }
    var groups by remember(s.knownGroups) { mutableStateOf(ListText.join(s.knownGroups)) }
    var url by remember(s.llmBaseUrl) { mutableStateOf(s.llmBaseUrl) }
    var key by remember(s.llmApiKey) { mutableStateOf(s.llmApiKey) }
    var model by remember(s.llmModel) { mutableStateOf(s.llmModel) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header("设置", "昵称、群名、可选的大模型")
        Panel {
            Text("风险与免责", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "读取微信页面属于非官方方式，存在被微信限制账号的风险。本应用只读：不自动发送消息、不加好友、不群发，" +
                    "回复由你自己复制后到微信里发送。请遵守微信使用规范，自行承担使用风险。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Panel {
            Text("权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("无障碍服务：" + if (serviceOn) "已开启" else "未开启", Modifier.weight(1f))
                OutlinedButton(onClick = actions.openAccessibility) { Text(if (serviceOn) "查看" else "去开启") }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("提醒通知", Modifier.weight(1f))
                OutlinedButton(onClick = actions.requestNotifications) { Text("允许通知") }
            }
        }
        Panel {
            Text("识别设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(nick, { nick = it }, Modifier.fillMaxWidth(), label = { Text("我在群里的昵称（判断是否被 @）") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                groups, { groups = it }, Modifier.fillMaxWidth(), label = { Text("已知群名（逗号或顿号分隔）") },
                supportingText = { Text("填上后，这些会话一定按群聊处理，避免把含冒号的私聊误判成群聊") },
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { Store.updateSettings { it.copy(myNickname = nick.trim(), knownGroups = ListText.split(groups)) } }) { Text("保存") }
        }
        Panel {
            Text("大模型建议回复（可选）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "不填就只用话术模板。填写后，点「AI 生成建议」时才会联网，把规则名、私聊/群聊和这一条消息的文字发到你填的接口。接口需兼容 OpenAI 格式，地址必须是 https。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("接口地址（https://…/v1）") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                key, { key = it }, Modifier.fillMaxWidth(), label = { Text("密钥") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型名") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { Store.updateSettings { it.copy(llmBaseUrl = url.trim(), llmApiKey = key.trim(), llmModel = model.trim()) } }) { Text("保存") }
        }
        Panel {
            Text("数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "规则、设置和命中的消息只存在本机，不上传。没有命中规则的消息不会保存。最多保留最近 ${Store.MAX_ALERTS} 条提醒。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { Store.clearAlerts() }) { Text("清空提醒记录") }
        }
        Spacer(Modifier.height(12.dp))
    }
}
