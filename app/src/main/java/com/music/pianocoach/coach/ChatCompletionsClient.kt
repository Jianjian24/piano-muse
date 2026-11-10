package com.music.pianocoach.coach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessageDto(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = false,
)

/** OpenAI 兼容的 `/chat/completions` 客户端（OpenAI / DeepSeek / 中转站等）。 */
class ChatCompletionsClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    /**
     * 非流式请求更稳；按段落拆成小块 emit，UI 仍有「打字」感。
     */
    fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
    ): Flow<String> = flow {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val body = json.encodeToString(
            ChatRequest(model = model, messages = messages, stream = false),
        ).toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        val text = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException(friendlyHttpError(resp.code, raw))
                }
                extractAssistantContent(raw)
                    ?: throw IllegalStateException("模型返回为空，请稍后重试")
            }
        }
        chunkForDisplay(text).forEach { emit(it) }
    }.flowOn(Dispatchers.Default)

    suspend fun completeChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
    ): String = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val body = json.encodeToString(
            ChatRequest(model = model, messages = messages, stream = false),
        ).toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(friendlyHttpError(resp.code, raw))
            }
            extractAssistantContent(raw)
                ?: throw IllegalStateException("模型返回为空，请稍后重试")
        }
    }

    suspend fun ping(baseUrl: String, apiKey: String, model: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val url = baseUrl.trimEnd('/') + "/chat/completions"
            val body = """{"model":"$model","messages":[{"role":"user","content":"hi"}],"stream":false,"max_tokens":5}"""
                .toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
            return@withContext try {
                http.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) Result.success(Unit)
                    else Result.failure(
                        IllegalStateException(friendlyHttpError(resp.code, resp.body?.string().orEmpty())),
                    )
                }
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.message ?: "网络错误"))
            }
        }

    private fun extractAssistantContent(raw: String): String? {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val choices = root["choices"]?.jsonArray ?: return null
            val first = choices.firstOrNull()?.jsonObject ?: return null
            val message = first["message"]?.jsonObject
            val content = message?.get("content")?.asText()
                ?: first["delta"]?.jsonObject?.get("content")?.asText()
            content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonElement.asText(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun friendlyHttpError(code: Int, raw: String): String {
        val apiMsg = try {
            json.parseToJsonElement(raw).jsonObject["error"]
                ?.jsonObject?.get("message")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
        return when {
            !apiMsg.isNullOrBlank() -> "请求失败 ($code)：$apiMsg"
            code == 401 -> "API Key 无效，请在设置中检查 Base URL 与密钥"
            code == 402 -> "账户余额不足"
            code == 429 -> "请求过于频繁，请稍后再试"
            else -> "请求失败 (HTTP $code)"
        }
    }

    private fun chunkForDisplay(text: String): List<String> {
        if (text.length <= 24) return listOf(text)
        val parts = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val end = (i + 20).coerceAtMost(text.length)
            parts.add(text.substring(i, end))
            i = end
        }
        return parts
    }
}
