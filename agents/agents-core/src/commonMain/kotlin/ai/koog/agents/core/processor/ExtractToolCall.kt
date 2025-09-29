package ai.koog.agents.core.processor

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * A data class representing a tool call message
 */
@Serializable
public data class ToolCall(
    val id: String? = null,
    val tool: String,
    val args: JsonObject
)

/**
 * A custom serializer for [ToolCall] that handles custom JSON property names.
 */
public class ToolCallSerializer(
    private val idNames: List<String> = listOf("id", "tool_call_id"),
    private val toolNames: List<String> = listOf("name", "tool", "tool_name"),
    private val argsNames: List<String> = listOf("arguments", "args", "parameters", "params"),
    private val allowNestedObject: Boolean = true,
) : KSerializer<ToolCall> by delegate {
    override fun deserialize(decoder: Decoder): ToolCall {
        require(decoder is JsonDecoder) { "This serializer can only be used with JSON" }

        val jsonElement = decoder.decodeJsonElement()
        require(jsonElement is JsonObject) { "Expected a JSON object" }

        return deserializeFromJsonObject(jsonElement, decoder.json)
    }

    private fun deserializeFromJsonObject(jsonObject: JsonObject, json: Json): ToolCall {
        // Check if this is a nested structure (e.g., {"function_call": {...}})
        val nestedObject = if (allowNestedObject) findNestedObject(jsonObject) else null
        var objectToDeserialize = nestedObject ?: jsonObject

        objectToDeserialize = updateKey(objectToDeserialize, idNames, "id")
        objectToDeserialize = updateKey(objectToDeserialize, toolNames, "tool")
        objectToDeserialize = updateKey(objectToDeserialize, argsNames, "args")

        return json.decodeFromJsonElement(delegate, objectToDeserialize)
    }

    private companion object {
        private val delegate = kotlinx.serialization.serializer<ToolCall>()

        private fun findNestedObject(jsonObject: JsonObject): JsonObject? =
            if (jsonObject.size == 1) {
                jsonObject.entries.first().value as? JsonObject
            } else {
                null
            }

        private fun updateKey(
            jsonObject: JsonObject,
            expectedKeys: List<String>,
            updatedKey: String
        ) = buildJsonObject {
            for ((key, value) in jsonObject) {
                put(if (key in expectedKeys) updatedKey else key, value)
            }
        }
    }
}

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

private fun extractJsonToolCall(
    toolCallMessage: String,
    metaInfo: ResponseMetaInfo,
    json: Json,
    serializer: KSerializer<ToolCall> = ToolCall.serializer()
): Message.Tool.Call? = runCatching {
    val toolCall = json.decodeFromString(serializer, fixIncorrectEscapes(toolCallMessage))
    Message.Tool.Call(
        toolCall.id,
        toolCall.tool,
        toolCall.args.toString(),
        metaInfo
    )
}.getOrNull()

/**
 * A response processor that extracts tool calls from JSON responses.
 *
 * @param json The JSON configuration to use.
 * @param serializer The serializer to use for deserializing tool calls. If null, the default serializer is used.
 */
@ResponseProcessorApi
public class ExtractJsonToolCall(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
    private val serializer: KSerializer<ToolCall> = ToolCallSerializer()
) : ResponseProcessor() {
    override suspend fun updateMessages(
        session: AIAgentLLMWriteSession,
        messages: List<Message.Response>
    ): List<Message.Response> = messages.map { message ->
        message as? Message.Tool.Call
            ?: extractJsonToolCall(message.content, message.metaInfo, json, serializer)
            ?: message
    }
}

/**
 * A response processor that extracts tool calls from tagged JSON responses.
 *
 * @param startTag The tag that marks the start of a tool call.
 * @param endTag The tag that marks the end of a tool call.
 * @param json The JSON configuration to use.
 * @param serializer The serializer to use for deserializing tool calls. If null, the default serializer is used.
 */
@ResponseProcessorApi
public class ExtractTaggedJsonToolCall(
    private val startTag: String = "<tool_call>",
    private val endTag: String = "</tool_call>",
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
    private val serializer: KSerializer<ToolCall> = ToolCallSerializer()
) : ResponseProcessor() {
    override suspend fun updateMessages(
        session: AIAgentLLMWriteSession,
        messages: List<Message.Response>
    ): List<Message.Response> = messages.map { message ->
        message as? Message.Tool.Call ?: searchToolCall(message) ?: message
    }

    private fun searchToolCall(message: Message.Response): Message.Tool.Call? {
        val content = message.content
        var startIndex = content.indexOf(startTag)

        while (startIndex != -1) {
            startIndex += startTag.length

            val endIndex = content.indexOf(endTag, startIndex)

            if (endIndex == -1) break

            val toolCall = content.substring(startIndex, endIndex)
            val result = extractJsonToolCall(toolCall, message.metaInfo, json, serializer)
            result?.let { return it }

            startIndex += startTag.length
        }

        return null
    }
}
