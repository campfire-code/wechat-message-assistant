package com.campfire.wechatassistant

import com.campfire.wechatassistant.core.Rule
import com.campfire.wechatassistant.core.RuleEngine
import com.campfire.wechatassistant.core.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEngineTest {
    private val price = Rule("p", "问价格", keywords = listOf("多少钱", "价格"))

    @Test fun keywordHit() {
        val h = RuleEngine.match(price, priv("这个多少钱呀"), "")
        assertEquals("多少钱", h?.matchedKeyword)
    }

    @Test fun keywordMiss() = assertNull(RuleEngine.match(price, priv("你好"), ""))

    @Test fun caseAndFullWidthInsensitive() {
        val r = Rule("v", "v", keywords = listOf("VIP"))
        assertNotNull(RuleEngine.match(r, priv("我要ｖｉｐ套餐"), ""))
    }

    @Test fun excludeKeywordBlocks() {
        val r = price.copy(excludeKeywords = listOf("不用报价"))
        assertNull(RuleEngine.match(r, priv("价格我知道了，不用报价"), ""))
        assertNotNull(RuleEngine.match(r, priv("价格是多少"), ""))
    }

    @Test fun disabledRuleNeverMatches() = assertNull(RuleEngine.match(price.copy(enabled = false), priv("价格"), ""))

    @Test fun emptyRuleMatchesNothing() = assertNull(RuleEngine.match(Rule("e", "空"), priv("任何内容"), ""))

    @Test fun blankKeywordsDoNotMatchEverything() =
        assertNull(RuleEngine.match(Rule("e", "空", keywords = listOf("", "  ")), priv("任何内容"), ""))

    @Test fun scopeGroupAndPrivate() {
        val g = price.copy(scope = Scope.GROUP)
        val p = price.copy(scope = Scope.PRIVATE)
        assertNull(RuleEngine.match(g, priv("价格"), ""))
        assertNotNull(RuleEngine.match(g, grp("价格"), ""))
        assertNotNull(RuleEngine.match(p, priv("价格"), ""))
        assertNull(RuleEngine.match(p, grp("价格"), ""))
    }

    @Test fun contactCondition() {
        val r = Rule("c", "重点客户", contacts = listOf("客户甲"))
        assertNotNull(RuleEngine.match(r, priv("在吗", "示例客户甲"), ""))
        assertNull(RuleEngine.match(r, priv("在吗", "示例客户丙"), ""))
        assertNotNull(RuleEngine.match(r, grp("在吗", speaker = "示例客户甲"), ""))
    }

    @Test fun groupConditionOnlyGroups() {
        val r = Rule("g", "业务群", groups = listOf("业务群"))
        assertNotNull(RuleEngine.match(r, grp("hi"), ""))
        assertNull(RuleEngine.match(r, grp("hi", group = "朋友群"), ""))
        assertNull(RuleEngine.match(r, priv("hi", "业务群联系人"), ""))
    }

    @Test fun conditionsAreAnded() {
        val r = Rule("a", "a", keywords = listOf("价格"), groups = listOf("业务群"), scope = Scope.GROUP)
        assertNotNull(RuleEngine.match(r, grp("价格多少"), ""))
        assertNull(RuleEngine.match(r, grp("你好"), ""))
        assertNull(RuleEngine.match(r, grp("价格多少", group = "别的群"), ""))
    }

    @Test fun atMe() {
        val r = Rule("at", "被@", requireAtMe = true)
        assertNotNull(RuleEngine.match(r, grp("@小篝 看下这个"), "小篝"))
        assertNull(RuleEngine.match(r, grp("@别人 看下这个"), "小篝"))
        assertNull(RuleEngine.match(r, grp("@小篝 看下"), ""))
        assertNull(RuleEngine.match(r, priv("@小篝 看下"), "小篝"))
    }

    @Test fun matchAllKeepsRuleOrder() {
        val r2 = Rule("q", "含价格的群消息", keywords = listOf("价格"), scope = Scope.GROUP)
        val hits = RuleEngine.matchAll(listOf(price, r2), grp("价格多少"), "")
        assertEquals(listOf("问价格", "含价格的群消息"), hits.map { it.rule.name })
    }
}
