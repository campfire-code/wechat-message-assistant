package com.campfire.wechatassistant

import com.campfire.wechatassistant.core.ChatListParser
import com.campfire.wechatassistant.core.ChatText
import com.campfire.wechatassistant.core.NotificationParser
import com.campfire.wechatassistant.core.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {
    @Test fun privateNotification() {
        val m = NotificationParser.parse("示例客户甲", "你好，多少钱", emptyList(), 5)!!
        assertFalse(m.isGroup)
        assertEquals("示例客户甲", m.sender)
        assertEquals("你好，多少钱", m.text)
        assertEquals(Source.NOTIFICATION, m.source)
    }

    @Test fun groupNotificationByColonHeuristic() {
        val m = NotificationParser.parse("示例业务群", "示例成员乙: @小篝 在吗", emptyList(), 0)!!
        assertTrue(m.isGroup)
        assertEquals("示例成员乙", m.sender)
        assertEquals("@小篝 在吗", m.text)
    }

    @Test fun knownGroupWithoutSpeaker() {
        val m = NotificationParser.parse("示例业务群", "[图片]", listOf("示例业务群"), 0)!!
        assertTrue(m.isGroup)
        assertEquals("", m.sender)
    }

    @Test fun knownPrivateNameWithColonStaysGroupOnlyIfListed() {
        // 未列入已知群名、预览带「昵称: 」→ 启发式当群聊；已知群名只会更确定，不会变成私聊
        assertTrue(NotificationParser.parse("某会话", "乙: 内容", emptyList(), 0)!!.isGroup)
        assertFalse(NotificationParser.parse("某会话", "注意：明天开会", emptyList(), 0)!!.isGroup)
        assertFalse(NotificationParser.parse("某会话", "看这个 http://x.example: 页面", emptyList(), 0)!!.isGroup)
    }

    @Test fun countPrefixStripped() {
        val m = NotificationParser.parse("示例客户丁", "[3条]在吗", emptyList(), 0)!!
        assertEquals("在吗", m.text)
        assertEquals("在吗" to 3, ChatText.stripCount("[3条]在吗"))
        assertEquals("[图片]" to null, ChatText.stripCount("[图片]"))
    }

    @Test fun nonMessageNotificationsIgnored() {
        assertNull(NotificationParser.parse(null, "x", emptyList(), 0))
        assertNull(NotificationParser.parse("示例客户甲", "  ", emptyList(), 0))
        assertNull(NotificationParser.parse("微信", "正在运行", emptyList(), 0))
    }

    @Test fun chatListRows() {
        val rows = ChatListParser.rows(loadNodes("chat_list.json"))
        assertEquals(5, rows.size)
        assertEquals("示例客户甲", rows[0].name)
        assertEquals("你好，这款多少钱？", rows[0].preview)
        assertEquals("10:21", rows[0].time)
        assertEquals(2, rows[0].unread)
        assertEquals(5, rows[1].unread)
        assertEquals(0, rows[2].unread)
        assertEquals("昨天", rows[2].time)
        assertEquals(3, rows[3].unread)       // 免打扰会话只有「[3条]」前缀
        assertEquals(99, rows[4].unread)      // 「99+」
        assertEquals("代号 123", rows[4].preview) // 预览里的数字不会被当成未读数
    }

    @Test fun chatListMessagesOnlyUnread() {
        val msgs = ChatListParser.parse(loadNodes("chat_list.json"), emptyList(), 100)
        assertEquals(listOf("示例客户甲", "示例业务群", "示例客户丁", "示例客户戊"), msgs.map { it.chat })
        assertTrue(msgs[1].isGroup)
        assertEquals("示例成员乙", msgs[1].sender)
        assertEquals("@小篝 周五的样品能发吗", msgs[1].text)
        assertEquals("在吗", msgs[2].text)
        assertTrue(msgs.all { it.source == Source.CHAT_LIST && it.timeMs == 100L })
    }

    @Test fun conversationPageGivesNothing() {
        assertEquals(emptyList<Any>(), ChatListParser.parse(loadNodes("chat_page.json"), emptyList(), 0))
        assertEquals(emptyList<Any>(), ChatListParser.parse(null, emptyList(), 0))
    }
}
