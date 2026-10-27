package com.campfire.wechatassistant.core

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 可选的大模型建议回复。接口地址、密钥、模型名都由用户在设置里填（兼容 OpenAI 的 chat/completions 格式）。
 * 只发出：规则名、会话类型、这一条消息的文字；不发联系人名和群名。密钥不写日志。
 */
object Llm {
    private val json = Json { ignoreUnknownKeys = true }

    /** 只接受 https。用户填的是接口根地址（如 https://host/v1）或完整的 …/chat/completions。 */
    fun endpoint(baseUrl: String): String? {
        val b = baseUrl.trim().trimEnd('/')
        if (!b.startsWith("https://") || b.length <= "https://".length || b.any { it.isWhitespace() }) return null
        return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
    }

    const val SYSTEM_PROMPT = "你是微信私域运营人员的回复助手。根据收到的一条消息，写一条简短、礼貌、口语化的中文回复建议，" +
        "不超过 80 个字。只输出回复内容本身，不要解释，不要编造价格、库存、承诺或联系方式；不确定的信息用「我确认一下再回复您」这类说法。"

    fun requestBody(model: String, alert: Alert): String {
        val m = alert.message
        val user = buildString {
            append("场景：").append(if (m.isGroup) "群聊" else "私聊").append('\n')
            append("命中的规则：").append(alert.ruleNames.joinToString("、")).append('\n')
            append("对方消息：").append(m.text)
        }
        return buildJsonObject {
            put("model", model)
            put("temperature", 0.5)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", SYSTEM_PROMPT) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
    }

    /** 取出 choices[0].message.content；格式不对返回 null。 */
    fun parseReply(body: String): String? = try {
        val root = json.parseToJsonElement(body).jsonObject
        val msg = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        msg?.get("content")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    /** 同步请求，调用方放到后台线程。错误信息里不含密钥。 */
    fun suggest(settings: AppSettings, alert: Alert, post: (url: String, key: String, body: String) -> Pair<Int, String> = ::httpPost): Result<String> {
        val url = endpoint(settings.llmBaseUrl) ?: return Result.failure(IllegalArgumentException("接口地址必须以 https:// 开头"))
        return try {
            val (code, body) = post(url, settings.llmApiKey, requestBody(settings.llmModel, alert))
            if (code !in 200..299) Result.failure(IllegalStateException("接口返回 $code"))
            else parseReply(body)?.let { Result.success(it) } ?: Result.failure(IllegalStateException("接口返回的格式无法识别"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("请求失败：" + (e.javaClass.simpleName)))
        }
    }

    private fun httpPost(url: String, key: String, body: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $key")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }
}
