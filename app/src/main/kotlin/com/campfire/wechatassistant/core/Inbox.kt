package com.campfire.wechatassistant.core

/** 汇总流程：去重 → 匹配规则 → 生成带建议回复的提醒。没有命中规则的消息不保存。 */
class Inbox(private val deduper: Deduper = Deduper(), private val newId: () -> String) {
    fun ingest(messages: List<IncomingMessage>, rules: List<Rule>, myNickname: String, now: Long): List<Alert> {
        val out = ArrayList<Alert>()
        for (m in messages) {
            if (!deduper.isNew(m, now)) continue
            val hits = RuleEngine.matchAll(rules, m, myNickname)
            if (hits.isEmpty()) continue
            val first = hits.firstOrNull { it.rule.template.isNotBlank() } ?: hits.first()
            out += Alert(
                id = newId(),
                message = m,
                ruleNames = hits.map { it.rule.name },
                matchedKeyword = hits.firstNotNullOfOrNull { it.matchedKeyword },
                suggestion = Template.suggestion(first, m, myNickname),
            )
        }
        return out
    }
}
