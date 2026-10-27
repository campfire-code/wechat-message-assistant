package com.campfire.wechatassistant.core

import kotlinx.serialization.Serializable

/** 页面控件的精简描述，和安卓的 AccessibilityNodeInfo 解耦，方便在 JVM 里用虚构节点树测试。 */
@Serializable
data class NodeInfo(
    val className: String = "",
    val text: String? = null,
    val desc: String? = null,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val children: List<NodeInfo> = emptyList(),
) {
    val label: String get() = (text ?: desc).orEmpty().trim()
    val width: Int get() = right - left
    val centerX: Int get() = (left + right) / 2
}

/**
 * 聊天列表解析（只读）：找到列表容器，把每一行拆成「名称、预览、时间、未读数」。
 * 靠控件的位置和文字形态判断（右侧的短数字是未读数，右侧的时间格式是时间，其余按从上到下排序，
 * 第一个是名称、后面是预览），不依赖微信内部的控件 id。真实微信各版本的页面结构
 * 没有逐一验证，解析不出来时会返回空列表，不会报错。
 */
object ChatListParser {
    data class Row(val name: String, val preview: String, val time: String?, val unread: Int)

    private val timeRegex = Regex(
        "^((上午|下午|凌晨|晚上)? ?\\d{1,2}:\\d{2}|昨天( \\d{1,2}:\\d{2})?|星期[一二三四五六日天]|周[一二三四五六日]|" +
            "\\d{4}/\\d{1,2}/\\d{1,2}|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2}月\\d{1,2}日|刚刚)$",
    )
    private val badgeRegex = Regex("^(\\d{1,3})(\\+?)$")
    private const val BADGE_MAX_WIDTH = 140
    private const val MAX_DEPTH = 40

    fun parse(root: NodeInfo?, knownGroups: List<String>, now: Long): List<IncomingMessage> {
        if (root == null) return emptyList()
        return rows(root).filter { it.unread > 0 }
            .mapNotNull { ChatText.toMessage(it.name, it.preview, knownGroups, now, Source.CHAT_LIST) }
    }

    fun rows(root: NodeInfo): List<Row> {
        val containers = ArrayList<NodeInfo>()
        fun find(n: NodeInfo, depth: Int) {
            if (depth > MAX_DEPTH) return
            val cls = n.className
            if (n.scrollable || cls.endsWith("ListView") || cls.endsWith("RecyclerView")) containers += n
            n.children.forEach { find(it, depth + 1) }
        }
        find(root, 0)
        val candidates = containers.map { c -> c.children.filter { texts(it).size >= 2 } }.filter { it.isNotEmpty() }
        var rowNodes = candidates.maxByOrNull { it.size }.orEmpty()
        if (rowNodes.isEmpty()) {
            // 没找到列表容器：退回到「可点击且含至少两段文字」的最内层节点
            val out = ArrayList<NodeInfo>()
            fun walk(n: NodeInfo, depth: Int) {
                if (depth > MAX_DEPTH) return
                val inner = n.children.filter { c -> c.clickable && texts(c).size >= 2 }
                if (n.clickable && texts(n).size >= 2 && inner.isEmpty()) out += n else n.children.forEach { walk(it, depth + 1) }
            }
            walk(root, 0)
            rowNodes = out
        }
        return rowNodes.mapNotNull { parseRow(it) }
    }

    private fun texts(n: NodeInfo): List<NodeInfo> {
        val out = ArrayList<NodeInfo>()
        fun walk(x: NodeInfo, depth: Int) {
            if (depth > MAX_DEPTH) return
            if (x.label.isNotEmpty() && x.children.isEmpty()) out += x
            x.children.forEach { walk(it, depth + 1) }
        }
        walk(n, 0)
        return out
    }

    private fun parseRow(row: NodeInfo): Row? {
        val all = texts(row)
        if (all.size < 2) return null
        val midX = (row.left + row.right) / 2
        var unread = 0
        var time: String? = null
        val rest = ArrayList<NodeInfo>()
        for (t in all) {
            val label = t.label
            val badge = badgeRegex.find(label)
            when {
                badge != null && t.width in 1..BADGE_MAX_WIDTH && t.centerX > midX && unread == 0 -> unread = badge.groupValues[1].toInt()
                timeRegex.matches(label) && t.centerX > midX && time == null -> time = label
                else -> rest += t
            }
        }
        val sorted = rest.sortedWith(compareBy({ it.top }, { it.left }))
        if (sorted.size < 2) return null
        val name = sorted[0].label
        val preview = sorted.drop(1).joinToString(" ") { it.label }
        val (_, count) = ChatText.stripCount(preview)
        if (unread == 0 && count != null) unread = count
        return Row(name, preview, time, unread)
    }
}
