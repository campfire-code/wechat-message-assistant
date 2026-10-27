package com.campfire.wechatassistant

import com.campfire.wechatassistant.core.Alert
import com.campfire.wechatassistant.core.AppSettings
import com.campfire.wechatassistant.core.Llm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmTest {
    private val alert = Alert("1", grp("多少钱", speaker = "示例成员乙", group = "示例业务群"), listOf("客户问价格"))
    private val cfg = AppSettings(llmBaseUrl = "https://llm.example/v1/", llmApiKey = "sk-fake", llmModel = "m1")

    @Test fun endpointRules() {
        assertEquals("https://llm.example/v1/chat/completions", Llm.endpoint("https://llm.example/v1/"))
        assertEquals("https://llm.example/v1/chat/completions", Llm.endpoint("https://llm.example/v1/chat/completions"))
        assertNull(Llm.endpoint("http://llm.example/v1"))
        assertNull(Llm.endpoint(""))
        assertNull(Llm.endpoint("https://"))
        assertNull(Llm.endpoint("https://a b"))
    }

    @Test fun readyNeedsAllFields() {
        assertTrue(cfg.llmReady)
        assertFalse(cfg.copy(llmApiKey = "").llmReady)
        assertFalse(cfg.copy(llmModel = " ").llmReady)
        assertFalse(AppSettings().llmReady)
    }

    @Test fun requestBodyHasNoChatNames() {
        val body = Llm.requestBody("m1", alert)
        val o = Json.parseToJsonElement(body).jsonObject
        assertEquals("m1", o["model"]!!.jsonPrimitive.content)
        val msgs = o["messages"]!!.jsonArray
        assertEquals("system", msgs[0].jsonObject["role"]!!.jsonPrimitive.content)
        val user = msgs[1].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(user.contains("多少钱") && user.contains("群聊") && user.contains("客户问价格"))
        assertFalse(body.contains("示例业务群") || body.contains("示例成员乙"))
    }

    @Test fun parseReply() {
        assertEquals("好的", Llm.parseReply("""{"choices":[{"message":{"role":"assistant","content":" 好的 "}}],"usage":{}}"""))
        assertNull(Llm.parseReply("""{"choices":[]}"""))
        assertNull(Llm.parseReply("not json"))
        assertNull(Llm.parseReply("""{"choices":[{"message":{"content":"  "}}]}"""))
    }

    @Test fun suggestUsesInjectedTransportAndSendsKeyOnlyAsHeader() {
        var seenKey = ""; var seenUrl = ""; var seenBody = ""
        val r = Llm.suggest(cfg, alert) { url, key, body ->
            seenUrl = url; seenKey = key; seenBody = body
            200 to """{"choices":[{"message":{"content":"稍后报价给您"}}]}"""
        }
        assertEquals("稍后报价给您", r.getOrNull())
        assertEquals("https://llm.example/v1/chat/completions", seenUrl)
        assertEquals("sk-fake", seenKey)
        assertFalse(seenBody.contains("sk-fake"))
    }

    @Test fun suggestErrorsDoNotLeakKey() {
        val bad = Llm.suggest(cfg, alert) { _, _, _ -> 401 to "sk-fake invalid" }
        assertEquals("接口返回 401", bad.exceptionOrNull()!!.message)
        val boom = Llm.suggest(cfg, alert) { _, key, _ -> throw java.io.IOException("failed with $key") }
        assertFalse(boom.exceptionOrNull()!!.message!!.contains("sk-fake"))
        val http = Llm.suggest(cfg.copy(llmBaseUrl = "http://x"), alert) { _, _, _ -> error("不应被调用") }
        assertTrue(http.isFailure)
        val weird = Llm.suggest(cfg, alert) { _, _, _ -> 200 to "<html>" }
        assertEquals("接口返回的格式无法识别", weird.exceptionOrNull()!!.message)
    }
}
