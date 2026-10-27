package com.campfire.wechatassistant.core

import kotlinx.serialization.Serializable

enum class Source { NOTIFICATION, CHAT_LIST }

/**
 * 读到的一条新消息。私聊时 [chat] 和 [sender] 都是联系人名；
 * 群聊时 [chat] 是群名，[sender] 是发言人（读不出来时为空）。
 */
@Serializable
data class IncomingMessage(
    val chat: String,
    val sender: String,
    val isGroup: Boolean,
    val text: String,
    val timeMs: Long,
    val source: Source,
)

@Serializable
enum class Scope { ALL, GROUP, PRIVATE }

/**
 * 一条提醒规则。关键词、联系人、群名各自是「任意一个命中」，不同类别之间是「同时满足」。
 * 关键词、联系人、群名都留空、也没勾选「群里被 @ 才算」时，规则不匹配任何消息（避免误成全匹配）。
 */
@Serializable
data class Rule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val scope: Scope = Scope.ALL,
    val keywords: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val contacts: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val requireAtMe: Boolean = false,
    val template: String = "",
)

data class RuleHit(val rule: Rule, val matchedKeyword: String?)

/** 命中规则的消息，用于在应用里汇总提醒。 */
@Serializable
data class Alert(
    val id: String,
    val message: IncomingMessage,
    val ruleNames: List<String>,
    val matchedKeyword: String? = null,
    val suggestion: String = "",
    val handled: Boolean = false,
)

@Serializable
data class AppSettings(
    val monitoring: Boolean = true,
    val myNickname: String = "",
    val knownGroups: List<String> = emptyList(),
    val llmBaseUrl: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "",
) {
    val llmReady: Boolean get() = Llm.endpoint(llmBaseUrl) != null && llmApiKey.isNotBlank() && llmModel.isNotBlank()
}
