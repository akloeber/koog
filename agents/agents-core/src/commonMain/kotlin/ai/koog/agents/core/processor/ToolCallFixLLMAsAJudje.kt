package ai.koog.agents.core.processor

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.dsl.extension.withTemporaryContext
import ai.koog.agents.core.prompt.Prompts.assessToolCallIntent
import ai.koog.agents.core.prompt.Prompts.fixToolArguments
import ai.koog.agents.core.prompt.Prompts.fixToolCall
import ai.koog.agents.core.prompt.Prompts.fixToolCallFormat
import ai.koog.agents.core.prompt.Prompts.fixToolName
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.text.TextContentBuilderBase
import ai.koog.prompt.text.text

/**
 * A response processor that fixes incorrect or unintended tool calls during language model interactions,
 * leveraging the model's own judgment as part of the correction process.
 *
 * This class handles processing of responses and applies logic to assess tool call intent, fix
 * incorrect tool calls, and manage retries.
 *
 * @property intentSystemMessage Definition of the system message content used to assess tool call intent.
 * @property fixSystemMessage Definition of the system message content used to fix incorrect tool calls.
 * @property getFeedback Feedback generated based on the language model's response.
 * @property fallback A function to be executed as a fallback when retries have been exhausted.
 * @property extractJsonToolCall A processing chain responsible for extracting tool call information from responses.
 * @property numRetries The number of retries allowed when attempting to fix a tool call.
 * @property showHistory Determines whether to preserve and showcase conversation history during processing.
 */
@ResponseProcessorApi
public class ToolCallFixLLMAsAJudge(
    private val intentSystemMessage: TextContentBuilderBase<*>.() -> Unit = { assessToolCallIntent() },
    private val fixSystemMessage: TextContentBuilderBase<*>.() -> Unit = { fixToolCall() },
    private val getFeedback: AIAgentLLMWriteSession.(Message.Response) -> String? = { defaultGetFeedback(it) },
    private val fallback: AIAgentLLMWriteSession.(Message.Response) -> Message.Response = { it },
    private val messagePreprocessing: ResponseProcessor = Chain(
        ExtractJsonToolCall(),
        ExtractTaggedJsonToolCall()
    ),
    private val numRetries: Int = 3,
    private val showHistory: Boolean = false,
) : ResponseProcessor() {
    init {
        require(numRetries > 0) { "numRetries must be greater than 0" }
    }

    override suspend fun updateMessages(
        session: AIAgentLLMWriteSession,
        messages: List<Message.Response>
    ): List<Message.Response> = messages.map { session.updateMessage(it) }

    private suspend fun AIAgentLLMWriteSession.updateMessage(message: Message.Response): Message.Response {
        logger.info { "Updating message: $message" }

        val message = messagePreprocessing.process(this, message)
        if (!isToolCallIntended(message)) return message

        val fixedMessage = withTemporaryContext {
            if (!showHistory) {
                prompt = prompt("fix-tool-call") {}
            }

            updatePrompt {
                system { fixSystemMessage() }
                message(message)
            }

            var result = message
            var i = 0

            while (i++ < numRetries) {
                val feedback = getFeedback(result) ?: break
                updatePrompt { user(feedback) }
                result = requestLLMProcessed()
            }

            if (i == numRetries) null else result
        }

        return (fixedMessage ?: fallback(message)).also { logger.info { "Updated message: $it" } }
    }

    private suspend fun AIAgentLLMWriteSession.isToolCallIntended(message: Message.Response) =
        message is Message.Tool.Call || withTemporaryContext {
            prompt = prompt("check-tool-call-intended") {
                system { intentSystemMessage() }
                user(message.content)
            }

            val response = requestLLMWithoutTools()

            response is Message.Tool.Call || response.content.contains("INTENDED_TOOL_CALL", ignoreCase = true)
        }

    private suspend fun AIAgentLLMWriteSession.requestLLMProcessed() =
        requestLLM(responseProcessor = messagePreprocessing)
}

/**
 * Default implementation for getting feedback on a message.
 *
 * @param message The message to get feedback for.
 * @return The feedback message, or null if no feedback is needed.
 */
private fun AIAgentLLMWriteSession.defaultGetFeedback(message: Message.Response): String? {
    if (message !is Message.Tool.Call) return text { fixToolCallFormat(tools) }

    if (!tools.any { it.name == message.tool}) {
        return text { fixToolName(message.tool, tools) }
    }

    val tool = toolRegistry.getTool(message.tool)

    try {
        tool.decodeArgs(message.contentJson)
    } catch (e: Exception) {
        val errorMessage = e.message ?: "Unknown error"
        return text { fixToolArguments(errorMessage, tool.descriptor) }
    }

    return null
}
