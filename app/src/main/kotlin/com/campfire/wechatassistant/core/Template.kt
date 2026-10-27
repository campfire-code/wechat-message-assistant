package com.campfire.wechatassistant.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 话术模板填充：单次扫描替换 `{变量}`，不认识的变量原样保留，替换进去的内容不会再被展开。 */
object Template {
    private val placeholder = Regex("\\{([^{}]+)\\}")

    fun fill(template: String, vars: Map<String, String>): String =
        placeholder.replace(template) { m -> vars[m.groupValues[1].trim()] ?: m.value }

    fun varsFor(msg: IncomingMessage, keyword: String?, myNickname: String): Map<String, String> = mapOf(
        "联系人" to msg.sender.ifBlank { msg.chat },
        "群名" to if (msg.isGroup) msg.chat else "",
        "消息" to msg.text,
        "关键词" to (keyword ?: ""),
        "时间" to SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(msg.timeMs)),
        "我的昵称" to myNickname,
    )

    fun suggestion(hit: RuleHit, msg: IncomingMessage, myNickname: String): String =
        if (hit.rule.template.isBlank()) "" else fill(hit.rule.template, varsFor(msg, hit.matchedKeyword, myNickname))
}

/** 设置页里「用逗号、顿号或换行分隔」的输入。 */
object ListText {
    fun split(s: String): List<String> = s.split(',', '，', '、', ';', '；', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    fun join(l: List<String>): String = l.joinToString("、")
}
