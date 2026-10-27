package com.campfire.wechatassistant.core

import java.text.Normalizer

/** 规则匹配：纯函数，不依赖安卓。 */
object RuleEngine {
    /** 全角转半角、转小写，便于不区分大小写和全半角地比较。 */
    fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()

    private fun containsAny(haystack: String, needles: List<String>): String? {
        val h = norm(haystack)
        for (n in needles) {
            val t = norm(n).trim()
            if (t.isNotEmpty() && h.contains(t)) return n.trim()
        }
        return null
    }

    fun atMe(msg: IncomingMessage, myNickname: String): Boolean {
        if (!msg.isGroup) return false
        val nick = norm(myNickname).trim()
        if (nick.isEmpty()) return false
        return norm(msg.text).contains("@$nick")
    }

    fun match(rule: Rule, msg: IncomingMessage, myNickname: String): RuleHit? {
        if (!rule.enabled) return null
        val hasCondition = rule.keywords.any { it.isNotBlank() } || rule.contacts.any { it.isNotBlank() } ||
            rule.groups.any { it.isNotBlank() } || rule.requireAtMe
        if (!hasCondition) return null
        when (rule.scope) {
            Scope.GROUP -> if (!msg.isGroup) return null
            Scope.PRIVATE -> if (msg.isGroup) return null
            Scope.ALL -> {}
        }
        if (rule.requireAtMe && !atMe(msg, myNickname)) return null
        if (rule.contacts.any { it.isNotBlank() } && containsAny(msg.sender, rule.contacts) == null) return null
        if (rule.groups.any { it.isNotBlank() } && (!msg.isGroup || containsAny(msg.chat, rule.groups) == null)) return null
        if (containsAny(msg.text, rule.excludeKeywords) != null) return null
        var matched: String? = null
        if (rule.keywords.any { it.isNotBlank() }) {
            matched = containsAny(msg.text, rule.keywords) ?: return null
        }
        return RuleHit(rule, matched)
    }

    /** 按规则顺序返回所有命中。 */
    fun matchAll(rules: List<Rule>, msg: IncomingMessage, myNickname: String): List<RuleHit> =
        rules.mapNotNull { match(it, msg, myNickname) }
}
