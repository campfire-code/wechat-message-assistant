package com.campfire.wechatassistant.core

/**
 * 把「会话名 + 一段预览文字」整理成消息。
 * 群聊预览的常见形式是「发言人: 内容」；如果会话名在设置里的「已知群名」中就当作群聊，
 * 否则只有预览文字符合「短昵称 + 英文冒号 + 空格」时才猜测为群聊。这是启发式，
 * 没有在真实微信上逐版本验证；设置里填上群名可以避免误判。
 */
object ChatText {
    private val countPrefix = Regex("^\\[(\\d{1,3})条]\\s*")
    private val groupSplit = Regex("^([^:\\n]{1,24}): (.+)$", RegexOption.DOT_MATCHES_ALL)

    /** 去掉预览文字开头的「[N条]」，返回（正文, 条数）。 */
    fun stripCount(raw: String): Pair<String, Int?> {
        val t = raw.trim()
        val m = countPrefix.find(t) ?: return t to null
        return t.substring(m.range.last + 1).trim() to m.groupValues[1].toInt()
    }

    fun toMessage(chat: String, rawPreview: String, knownGroups: List<String>, now: Long, source: Source): IncomingMessage? {
        val name = chat.trim()
        val (body, _) = stripCount(rawPreview)
        if (name.isEmpty() || body.isEmpty()) return null
        val known = knownGroups.any { RuleEngine.norm(it).trim() == RuleEngine.norm(name) && it.isNotBlank() }
        val split = groupSplit.find(body)
        return when {
            split != null && (known || looksLikeGroupPreview(split.groupValues[1])) ->
                IncomingMessage(name, split.groupValues[1].trim(), true, split.groupValues[2].trim(), now, source)
            known -> IncomingMessage(name, "", true, body, now, source)
            else -> IncomingMessage(name, name, false, body, now, source)
        }
    }

    private fun looksLikeGroupPreview(speaker: String) = speaker.isNotBlank() && !speaker.startsWith("http")
}

/** 通知文字解析。 */
object NotificationParser {
    /** 返回 null 表示这条通知不是聊天消息（标题或内容为空，或者是应用名本身）。 */
    fun parse(title: CharSequence?, text: CharSequence?, knownGroups: List<String>, now: Long): IncomingMessage? {
        val t = title?.toString()?.trim().orEmpty()
        val x = text?.toString()?.trim().orEmpty()
        if (t.isEmpty() || x.isEmpty() || t == "微信") return null
        return ChatText.toMessage(t, x, knownGroups, now, Source.NOTIFICATION)
    }
}
