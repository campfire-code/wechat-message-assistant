package com.campfire.wechatassistant.core

/**
 * 消息去重。通知和聊天列表会反复出现同一条消息，所以用「会话 + 发言人 + 内容」做键。
 * 滑动窗口：同一条消息只要还在被看到，就不会重复提醒；超过 [ttlMs] 没再看到，才算新消息。
 */
class Deduper(private val ttlMs: Long = 30 * 60_000L, private val maxEntries: Int = 2000) {
    private val seen = LinkedHashMap<String, Long>()

    fun isNew(msg: IncomingMessage, now: Long): Boolean {
        prune(now)
        val key = keyOf(msg)
        val fresh = !seen.containsKey(key)
        seen.remove(key)
        seen[key] = now
        while (seen.size > maxEntries) seen.remove(seen.keys.first())
        return fresh
    }

    private fun prune(now: Long) {
        val it = seen.entries.iterator()
        while (it.hasNext()) if (now - it.next().value > ttlMs) it.remove()
    }

    companion object {
        fun keyOf(m: IncomingMessage): String =
            listOf(m.chat, m.sender, m.text).joinToString("\u0001") { RuleEngine.norm(it).replace(Regex("\\s+"), " ").trim() }
    }
}
