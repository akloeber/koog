package ai.koog.agents.core.processor

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
private data class ToolCallMessage(
    @JsonNames("id", "tool_call_id")
    val id: String? = null,
    @JsonNames("name", "tool", "tool_name")
    val tool: String,
    @JsonNames("arguments", "args", "parameters", "params")
    val args: JsonObject
)

private val IncorrectEscapesMap: Map<String, String> = mapOf(
    "\\'" to "'"
)

private fun fixIncorrectEscapes(
    input: String,
): String {
    var result = input
    for ((incorrect, correct) in IncorrectEscapesMap) {
        result = result.replace(incorrect, correct)
    }
    return result
}

private fun deserializeToolCallMessage(
    toolCall: String,
    json: Json,
): ToolCallMessage {
    val fixedToolCall = fixIncorrectEscapes(toolCall)

    return try {
        json.decodeFromString<ToolCallMessage>(fixedToolCall)
    } catch (e: Exception) {
        val jsonObject = json.parseToJsonElement(fixedToolCall).jsonObject
        require(jsonObject.size == 1) { "Failed to deserialize from standard structure" }

        val nestedObject = jsonObject.values.first()
        json.decodeFromJsonElement(ToolCallMessage.serializer(), nestedObject)
    }
}

private fun extractJsonToolCall(
    toolCall: String,
    metaInfo: ResponseMetaInfo,
    json: Json,
): Message.Tool.Call? = runCatching {
    val toolCallMessage = deserializeToolCallMessage(toolCall, json)
    Message.Tool.Call(
        toolCallMessage.id,
        toolCallMessage.tool,
        toolCallMessage.args.toString(),
        metaInfo
    )
}.getOrNull()

/**
 * A response processor that extracts tool calls from JSON responses.
 *
 * @param extractJsonToolCall A function that extracts a tool call from a JSON string.
 * @param json The JSON configuration to use.
 */
public class ExtractJsonToolCall(
    private val extractJsonToolCall: (String, ResponseMetaInfo, Json) -> Message.Tool.Call? = ::extractJsonToolCall,
    private val json: Json = Json {},
) : ResponseProcessor() {
    override suspend fun updateMessages(
        session: AIAgentLLMWriteSession,
        messages: List<Message.Response>
    ): List<Message.Response> = messages.map { message ->
        message as? Message.Tool.Call ?: (extractJsonToolCall(message.content, message.metaInfo, json) ?: message)
    }
}

/**
 * A response processor that extracts tool calls from tagged JSON responses.
 *
 * @param startTag The tag that marks the start of a tool call.
 * @param endTag The tag that marks the end of a tool call.
 * @param extractJsonToolCall A function that extracts a tool call from a JSON string.
 * @param json The JSON configuration to use.
 */
public class ExtractTaggedJsonToolCall(
    private val startTag: String = "<tool_call>",
    private val endTag: String = "</tool_call>",
    private val extractJsonToolCall: (String, ResponseMetaInfo, Json) -> Message.Tool.Call? = ::extractJsonToolCall,
    private val json: Json = Json {},
) : ResponseProcessor() {
    override suspend fun updateMessages(
        session: AIAgentLLMWriteSession,
        messages: List<Message.Response>
    ): List<Message.Response> = messages.map { message ->
        message as? Message.Tool.Call ?: (searchToolCall(message) ?: message)
    }

    private fun searchToolCall(message: Message.Response): Message.Tool.Call? {
        val content = message.content
        var startIndex = content.indexOf(startTag)

        while (startIndex != -1) {
            startIndex += startTag.length

            val endIndex = content.indexOf(endTag, startIndex)

            if (endIndex == -1) break

            val toolCall = content.substring(startIndex, endIndex)
            extractJsonToolCall(toolCall, message.metaInfo, json)?.let { return it }

            startIndex += startTag.length
        }

        return null
    }
}
