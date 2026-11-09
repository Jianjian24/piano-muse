package com.music.pianocoach.coach

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class UiChatMessage(
    val role: String,
    val content: String,
)

class CoachAgent(
    private val client: DeepSeekClient,
) {

    fun buildSystemPrompt(skillLevel: String): String = """
        你是「钢琴教练」陪练助手，面向 $skillLevel 学习者。
        你会收到用户最近弹奏的结构化 JSON 摘要（音高、节奏、跟练结果），请结合数据给建议。
        要求：
        - 用中文，具体可练习 3～5 分钟，避免长篇乐理。
        - 若用户问加花，给出 1～2 种音名/节拍级别的装饰思路，不要编造未出现的音。
        - 调性字段为启发式，勿绝对化。
        - 只讨论钢琴与音乐练习。
    """.trimIndent()

    fun streamCoachReply(
        baseUrl: String,
        apiKey: String,
        model: String,
        skillLevel: String,
        summaryJson: String,
        userMessage: String,
        history: List<UiChatMessage> = emptyList(),
    ): Flow<String> {
        if (apiKey.isBlank()) {
            return flow { emit("请先在「设置」中填写 DeepSeek API Key。") }
        }
        val system = buildSystemPrompt(skillLevel)
        val contextBlock = "【演奏数据 JSON】\n$summaryJson"
        val messages = buildList {
            add(ChatMessageDto("system", system))
            add(ChatMessageDto("system", contextBlock))
            history.takeLast(8).forEach { add(ChatMessageDto(it.role, it.content)) }
            add(ChatMessageDto("user", userMessage))
        }
        return client.streamChat(baseUrl, apiKey, model, messages)
    }

    fun defaultAnalyzePrompt(): String =
        "请根据上面的演奏数据，简要评价这一段弹奏（音准、节奏、连贯性），并给一个接下来 3～5 分钟的练习任务。"

    fun drillReviewPrompt(): String =
        "我刚刚完成了一次音阶跟练，请根据 JSON 里的 drill 字段给一次简短点评和改进建议。"
}
