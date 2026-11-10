package com.music.pianocoach.coach

import com.music.pianocoach.drill.MidiPieceEncoder
import com.music.pianocoach.drill.PracticeDrill
import com.music.pianocoach.drill.PracticeHandFilter
import com.music.pianocoach.drill.PracticeLayer
import com.music.pianocoach.drill.PracticeLayerKind
import com.music.pianocoach.drill.PracticePlan
import com.music.pianocoach.drill.SongPiece
import com.music.pianocoach.drill.totalTicks
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

class PiecePracticeAnalyzer(
    private val client: ChatCompletionsClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** Harness 自愈：解析失败时带原输出重试，降低坏 JSON 影响 */
        const val PLAN_PARSE_MAX_ATTEMPTS = 3
        private const val MAX_ASSISTANT_CHARS_FOR_REPAIR = 12_000
    }

    suspend fun analyze(
        piece: SongPiece,
        baseUrl: String,
        apiKey: String,
        model: String,
        skillLevel: String,
    ): Result<PracticePlan> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("请先在设置中填写大模型 API Key"))
        }
        if (piece.timedNotes.isEmpty()) {
            return Result.failure(IllegalStateException("曲目没有可分析的音符"))
        }
        val midiText = MidiPieceEncoder.encode(piece)
        val system = systemPrompt(skillLevel, piece.totalTicks())
        val seedUser = "请为下面这首曲子生成分层练习计划。\n\n$midiText"
        return try {
            var messages = listOf(
                ChatMessageDto("system", system),
                ChatMessageDto("user", seedUser),
            )
            var lastReason = "未知错误"
            repeat(PLAN_PARSE_MAX_ATTEMPTS) { attempt ->
                val raw = client.completeChat(baseUrl, apiKey, model, messages)
                val built = tryBuildPlan(raw, piece, model)
                built.onSuccess { return Result.success(it) }
                lastReason = built.exceptionOrNull()?.message ?: lastReason
                if (attempt == PLAN_PARSE_MAX_ATTEMPTS - 1) {
                    return Result.failure(
                        IllegalStateException("练习计划生成失败（已重试 $PLAN_PARSE_MAX_ATTEMPTS 次）：$lastReason"),
                    )
                }
                messages = messages + listOf(
                    ChatMessageDto("assistant", raw.take(MAX_ASSISTANT_CHARS_FOR_REPAIR)),
                    ChatMessageDto("user", repairUserPrompt(lastReason)),
                )
            }
            Result.failure(IllegalStateException("练习计划生成失败：$lastReason"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun repairUserPrompt(reason: String): String = """
        你上一次回复无法生成练习计划，原因：$reason
        请严格按 system 要求，只输出一个 JSON 对象（无 Markdown 代码块、无解释文字）。
    """.trimIndent()

    private fun tryBuildPlan(raw: String, piece: SongPiece, model: String): Result<PracticePlan> {
        val dto = parsePlanJson(raw)
            ?: return Result.failure(IllegalStateException("模型返回无法解析为 JSON"))
        val plan = toPlan(dto, piece, model)
        if (plan.layers.all { it.drills.isEmpty() }) {
            return Result.failure(IllegalStateException("模型未给出可用练习条目"))
        }
        val maxTick = piece.totalTicks().coerceAtLeast(1)
        val invalidRange = plan.layers.any { layer ->
            layer.drills.any { it.endTick <= it.startTick || it.endTick > maxTick }
        }
        if (invalidRange) {
            return Result.failure(IllegalStateException("drills 的 tick 区间不合法"))
        }
        return Result.success(plan)
    }

    private fun systemPrompt(skillLevel: String, totalTicks: Int): String = """
        你是钢琴陪练教练，面向「$skillLevel」学员。
        根据几乎完整的 MIDI 事件列表，为该曲生成分层练习计划。
        必须只输出一个 JSON 对象（不要 Markdown 代码围栏，不要解释），结构如下：
        {
          "summary": "一句话曲目与练习总策略",
          "layers": [
            {
              "kind": "FAMILIAR|TACKLE|CONNECT",
              "title": "层标题",
              "drills": [
                {
                  "title": "练习名",
                  "goal": "目标说明",
                  "startTick": 0,
                  "endTick": 1000,
                  "hand": "LEFT|RIGHT|BOTH",
                  "tempoHint": "可选，如慢速"
                }
              ]
            }
          ]
        }
        硬性要求：
        - 必须包含三层：FAMILIAR（熟悉）、TACKLE（攻克难点）、CONNECT（串联），顺序固定。
        - 每层 1～4 条 drills；每条 startTick/endTick 必须落在 0..$totalTicks，且 endTick>startTick。
        - 片段要能单独练：不要整曲复制三条；优先短片段、单手或双手难点。
        - hand 与难点匹配；不确定时用 BOTH。
        - 用中文标题与 goal。
    """.trimIndent()

    private fun parsePlanJson(raw: String): AiPlanDto? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.decodeFromString<AiPlanDto>(cleaned.substring(start, end + 1))
        }.getOrNull()
    }

    private fun toPlan(dto: AiPlanDto, piece: SongPiece, model: String): PracticePlan {
        val maxTick = piece.totalTicks().coerceAtLeast(1)
        val byKind = linkedMapOf(
            PracticeLayerKind.FAMILIAR to "熟悉",
            PracticeLayerKind.TACKLE to "攻克",
            PracticeLayerKind.CONNECT to "串联",
        )
        val fromModel = dto.layers.mapNotNull { layer ->
            val kind = parseKind(layer.kind) ?: return@mapNotNull null
            val drills = layer.drills.mapIndexedNotNull { idx, d ->
                val start = d.startTick.coerceIn(0, maxTick - 1)
                val end = d.endTick.coerceIn(start + 1, maxTick)
                if (end <= start) return@mapIndexedNotNull null
                PracticeDrill(
                    id = "d_${kind.name.lowercase()}_$idx",
                    title = d.title.ifBlank { "练习 ${idx + 1}" },
                    goal = d.goal.orEmpty(),
                    startTick = start,
                    endTick = end,
                    hand = parseHand(d.hand),
                    tempoHint = d.tempoHint.orEmpty(),
                )
            }
            PracticeLayer(
                kind = kind,
                title = layer.title?.takeIf { it.isNotBlank() } ?: byKind.getValue(kind),
                drills = drills,
            )
        }.associateBy { it.kind }

        val layers = byKind.map { (kind, defaultTitle) ->
            fromModel[kind] ?: PracticeLayer(kind = kind, title = defaultTitle, drills = emptyList())
        }

        return PracticePlan(
            id = "plan_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
            songId = piece.id,
            createdAtEpochMs = System.currentTimeMillis(),
            model = model,
            summary = dto.summary.orEmpty(),
            layers = layers,
        )
    }

    private fun parseKind(raw: String?): PracticeLayerKind? = when (raw?.trim()?.uppercase()) {
        "FAMILIAR", "熟悉" -> PracticeLayerKind.FAMILIAR
        "TACKLE", "攻克", "DIFFICULTY" -> PracticeLayerKind.TACKLE
        "CONNECT", "串联", "LINK" -> PracticeLayerKind.CONNECT
        else -> null
    }

    private fun parseHand(raw: String?): PracticeHandFilter = when (raw?.trim()?.uppercase()) {
        "LEFT", "L", "左手" -> PracticeHandFilter.LEFT
        "RIGHT", "R", "右手" -> PracticeHandFilter.RIGHT
        else -> PracticeHandFilter.BOTH
    }
}

@Serializable
private data class AiPlanDto(
    val summary: String? = null,
    val layers: List<AiLayerDto> = emptyList(),
)

@Serializable
private data class AiLayerDto(
    val kind: String? = null,
    val title: String? = null,
    val drills: List<AiDrillDto> = emptyList(),
)

@Serializable
private data class AiDrillDto(
    val title: String = "",
    val goal: String? = null,
    val startTick: Int = 0,
    val endTick: Int = 0,
    val hand: String? = null,
    val tempoHint: String? = null,
)
