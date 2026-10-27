package com.campfire.wechatassistant.data

import android.content.Context
import com.campfire.wechatassistant.core.Alert
import com.campfire.wechatassistant.core.AppSettings
import com.campfire.wechatassistant.core.Deduper
import com.campfire.wechatassistant.core.IncomingMessage
import com.campfire.wechatassistant.core.Inbox
import com.campfire.wechatassistant.core.Rule
import com.campfire.wechatassistant.core.Scope
import com.campfire.wechatassistant.core.Source
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 规则、设置、提醒记录都只存在本机应用私有目录（SharedPreferences），不上传。 */
object Store {
    const val MAX_ALERTS = 300
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    private val _settings = MutableStateFlow(AppSettings())
    val rules: StateFlow<List<Rule>> = _rules
    val alerts: StateFlow<List<Alert>> = _alerts
    val settings: StateFlow<AppSettings> = _settings

    private var appCtx: Context? = null
    private var persist = true
    private val inbox = Inbox(Deduper()) { UUID.randomUUID().toString() }

    @Synchronized
    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        val sp = prefs()
        _rules.value = sp.getString("rules", null)?.let { runCatching { json.decodeFromString(ListSerializer(Rule.serializer()), it) }.getOrNull() }
            ?: defaultRules()
        _alerts.value = sp.getString("alerts", null)?.let { runCatching { json.decodeFromString(ListSerializer(Alert.serializer()), it) }.getOrNull() }
            ?: emptyList()
        _settings.value = sp.getString("settings", null)?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()
    }

    private fun prefs() = appCtx!!.getSharedPreferences("store", Context.MODE_PRIVATE)

    private fun save() {
        if (!persist) return
        prefs().edit()
            .putString("rules", json.encodeToString(ListSerializer(Rule.serializer()), _rules.value))
            .putString("alerts", json.encodeToString(ListSerializer(Alert.serializer()), _alerts.value))
            .putString("settings", json.encodeToString(AppSettings.serializer(), _settings.value))
            .apply()
    }

    @Synchronized
    fun ingest(messages: List<IncomingMessage>): List<Alert> {
        val s = _settings.value
        val fresh = inbox.ingest(messages, _rules.value, s.myNickname, System.currentTimeMillis())
        if (fresh.isNotEmpty()) {
            _alerts.value = (fresh.reversed() + _alerts.value).take(MAX_ALERTS)
            save()
        }
        return fresh
    }

    @Synchronized fun upsertRule(r: Rule) {
        val cur = _rules.value
        _rules.value = if (cur.any { it.id == r.id }) cur.map { if (it.id == r.id) r else it } else cur + r
        save()
    }

    @Synchronized fun deleteRule(id: String) { _rules.value = _rules.value.filterNot { it.id == id }; save() }
    @Synchronized fun updateSettings(f: (AppSettings) -> AppSettings) { _settings.value = f(_settings.value); save() }
    @Synchronized fun markHandled(id: String, handled: Boolean = true) {
        _alerts.value = _alerts.value.map { if (it.id == id) it.copy(handled = handled) else it }; save()
    }
    @Synchronized fun clearAlerts() { _alerts.value = emptyList(); save() }

    fun newRuleId(): String = UUID.randomUUID().toString()

    fun defaultRules(): List<Rule> = listOf(
        Rule("r-price", "客户问价格", keywords = listOf("多少钱", "价格", "报价", "怎么卖", "优惠"),
            template = "您好{联系人}，感谢咨询。具体价格要看您的需求，方便说下数量和用途吗？我按需求给您出报价。"),
        Rule("r-atme", "群里有人 @我", scope = Scope.GROUP, requireAtMe = true,
            template = "收到，我看一下，稍后回复您。"),
        Rule("r-after", "售后问题", keywords = listOf("坏了", "退款", "退货", "投诉", "不能用"),
            template = "非常抱歉给您添麻烦了，请把订单号和问题截图发我，我马上帮您处理。"),
    )

    /** 仅供截图：载入虚构数据，只放内存，不写入本机存储。 */
    fun loadDemo(now: Long) {
        persist = false
        _rules.value = defaultRules()
        _settings.value = AppSettings(monitoring = true, myNickname = "小篝")
        fun m(chat: String, sender: String, group: Boolean, text: String, ago: Long) =
            IncomingMessage(chat, sender, group, text, now - ago * 60_000, Source.NOTIFICATION)
        _alerts.value = listOf(
            Alert("d1", m("示例客户甲", "示例客户甲", false, "你好，这款现在多少钱？有优惠吗", 2), listOf("客户问价格"), "多少钱",
                "您好示例客户甲，感谢咨询。具体价格要看您的需求，方便说下数量和用途吗？我按需求给您出报价。"),
            Alert("d2", m("示例业务群", "示例成员乙", true, "@小篝 周五的样品能发出来吗", 9), listOf("群里有人 @我"), null,
                "收到，我看一下，稍后回复您。"),
            Alert("d3", m("示例客户丙", "示例客户丙", false, "上次买的东西不能用了，想退款", 26), listOf("售后问题"), "不能用",
                "非常抱歉给您添麻烦了，请把订单号和问题截图发我，我马上帮您处理。", handled = true),
        )
    }
}
