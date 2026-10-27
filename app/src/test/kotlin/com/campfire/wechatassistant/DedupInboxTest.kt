package com.campfire.wechatassistant

import com.campfire.wechatassistant.core.Deduper
import com.campfire.wechatassistant.core.Inbox
import com.campfire.wechatassistant.core.Rule
import com.campfire.wechatassistant.core.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupInboxTest {
    private val min = 60_000L

    @Test fun sameMessageTwiceIsDuplicate() {
        val d = Deduper()
        assertTrue(d.isNew(priv("多少钱"), 0))
        assertFalse(d.isNew(priv("多少钱"), 1000))
    }

    @Test fun differentSourceSameContentIsDuplicate() {
        val d = Deduper()
        assertTrue(d.isNew(priv("多少钱"), 0))
        assertFalse(d.isNew(priv("多少钱").copy(source = Source.CHAT_LIST), 5))
    }

    @Test fun keyIgnoresCaseWidthAndSpaces() {
        val d = Deduper()
        assertTrue(d.isNew(priv("Hello  World"), 0))
        assertFalse(d.isNew(priv("ｈｅｌｌｏ world"), 1))
    }

    @Test fun differentChatOrSenderOrTextIsNew() {
        val d = Deduper()
        assertTrue(d.isNew(grp("hi"), 0))
        assertTrue(d.isNew(grp("hi", speaker = "别人"), 0))
        assertTrue(d.isNew(grp("hi", group = "别的群"), 0))
        assertTrue(d.isNew(grp("hello"), 0))
    }

    @Test fun slidingWindowKeepsVisibleMessageSuppressed() {
        val d = Deduper(ttlMs = 30 * min)
        assertTrue(d.isNew(priv("x"), 0))
        // 每 20 分钟又看到一次：一直不算新消息
        assertFalse(d.isNew(priv("x"), 20 * min))
        assertFalse(d.isNew(priv("x"), 40 * min))
        // 超过 30 分钟没再看到，才算新消息
        assertTrue(d.isNew(priv("x"), 71 * min))
    }

    @Test fun capacityEvictsOldest() {
        val d = Deduper(ttlMs = 1_000_000L, maxEntries = 2)
        d.isNew(priv("1"), 0); d.isNew(priv("2"), 1); d.isNew(priv("3"), 2)
        assertTrue(d.isNew(priv("1"), 3))
    }

    private val rules = listOf(
        Rule("p", "客户问价格", keywords = listOf("多少钱"), template = "您好{联系人}，价格稍后发您"),
        Rule("a", "群里有人 @我", requireAtMe = true, template = ""),
    )

    private fun inbox(): Inbox {
        var n = 0
        return Inbox(Deduper()) { "id${n++}" }
    }

    @Test fun ingestKeepsOnlyHitsAndFillsSuggestion() {
        val out = inbox().ingest(listOf(priv("多少钱"), priv("早上好")), rules, "小篝", 0)
        assertEquals(1, out.size)
        assertEquals(listOf("客户问价格"), out[0].ruleNames)
        assertEquals("您好示例客户甲，价格稍后发您", out[0].suggestion)
        assertEquals("多少钱", out[0].matchedKeyword)
    }

    @Test fun ingestDeduplicatesAcrossCalls() {
        val ib = inbox()
        assertEquals(1, ib.ingest(listOf(priv("多少钱")), rules, "", 0).size)
        assertEquals(0, ib.ingest(listOf(priv("多少钱")), rules, "", 1000).size)
    }

    @Test fun multipleHitsMergeNamesAndUseFirstTemplate() {
        val r = rules + Rule("g", "群里问价", keywords = listOf("多少钱"), template = "second")
        val out = inbox().ingest(listOf(grp("@小篝 多少钱")), r, "小篝", 0)
        assertEquals(listOf("客户问价格", "群里有人 @我", "群里问价"), out[0].ruleNames)
        assertTrue(out[0].suggestion.startsWith("您好示例成员乙"))
    }

    @Test fun hitWithoutTemplateGivesEmptySuggestion() {
        val out = inbox().ingest(listOf(grp("@小篝 在吗")), rules, "小篝", 0)
        assertEquals("", out[0].suggestion)
    }
}
