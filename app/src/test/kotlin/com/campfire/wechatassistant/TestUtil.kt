package com.campfire.wechatassistant

import com.campfire.wechatassistant.core.IncomingMessage
import com.campfire.wechatassistant.core.NodeInfo
import com.campfire.wechatassistant.core.Source
import kotlinx.serialization.json.Json

fun priv(text: String, who: String = "示例客户甲", t: Long = 0L) = IncomingMessage(who, who, false, text, t, Source.NOTIFICATION)
fun grp(text: String, speaker: String = "示例成员乙", group: String = "示例业务群", t: Long = 0L) =
    IncomingMessage(group, speaker, true, text, t, Source.NOTIFICATION)

fun loadNodes(name: String): NodeInfo {
    val s = object {}.javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    return Json { ignoreUnknownKeys = true }.decodeFromString(NodeInfo.serializer(), s)
}
