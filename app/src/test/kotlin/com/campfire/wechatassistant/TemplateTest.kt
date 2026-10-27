package com.campfire.wechatassistant

import com.campfire.wechatassistant.core.ListText
import com.campfire.wechatassistant.core.Rule
import com.campfire.wechatassistant.core.RuleHit
import com.campfire.wechatassistant.core.Template
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateTest {
    @Test fun fillsKnownVariables() =
        assertEquals("您好张三，收到：多少钱", Template.fill("您好{联系人}，收到：{消息}", mapOf("联系人" to "张三", "消息" to "多少钱")))

    @Test fun unknownVariableKept() = assertEquals("你好{不存在}", Template.fill("你好{不存在}", mapOf("联系人" to "x")))

    @Test fun valuesAreNotReExpanded() = assertEquals("收到：{联系人}", Template.fill("收到：{消息}", mapOf("消息" to "{联系人}", "联系人" to "张三")))

    @Test fun spacesInsidePlaceholderTolerated() = assertEquals("甲", Template.fill("{ 联系人 }", mapOf("联系人" to "甲")))

    @Test fun suggestionForGroupUsesSpeaker() {
        val rule = Rule("r", "r", keywords = listOf("价格"), template = "{联系人}您好，{群名}里的问题我来跟进，关键词{关键词}")
        val s = Template.suggestion(RuleHit(rule, "价格"), grp("价格多少", speaker = "示例成员乙", group = "示例业务群"), "")
        assertEquals("示例成员乙您好，示例业务群里的问题我来跟进，关键词价格", s)
    }

    @Test fun suggestionPrivateAndBlankTemplate() {
        val rule = Rule("r", "r", keywords = listOf("价格"), template = "{联系人}-{群名}-")
        assertEquals("示例客户甲--", Template.suggestion(RuleHit(rule, null), priv("x"), ""))
        assertEquals("", Template.suggestion(RuleHit(rule.copy(template = " "), null), priv("x"), ""))
    }

    @Test fun listTextSplit() {
        assertEquals(listOf("a", "b", "c"), ListText.split("a，b、c\n b ;"))
        assertEquals("a、b", ListText.join(listOf("a", "b")))
    }
}
