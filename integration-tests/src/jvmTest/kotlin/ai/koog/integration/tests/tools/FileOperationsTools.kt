package ai.koog.integration.tests.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

class FileOperationsTools {
    val fileContentsByPath = mutableMapOf<String, String>()

    val createNewFileWithTextTool = CreateNewFileWithTextTool(this)
    val readFileContentTool = ReadFileContentTool(this)
    val appendToFileTool = AppendToFileTool(this)
    val replaceFileContentTool = ReplaceFileContentTool(this)

    // Tool for creating a new file with text content
    class CreateNewFileWithTextTool(private val fileOperationsTools: FileOperationsTools) : SimpleTool<CreateNewFileWithTextTool.Args>() {
        @Serializable
        data class Args(
            val pathInProject: String,
            val text: String
        ) : ToolArgs

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "createNewFileWithText",
            description = "Creates a new file at the specified path with the provided text content",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "pathInProject",
                    description = "Path to the file to be created. The path is relative to the project root.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "text",
                    description = "Text content to write to the file",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun doExecute(args: Args): String {
            return fileOperationsTools.createNewFileWithText(args.pathInProject, args.text)
        }
    }

    // Tool for reading file content
    class ReadFileContentTool(private val fileOperationsTools: FileOperationsTools) : SimpleTool<ReadFileContentTool.Args>() {
        @Serializable
        data class Args(
            val pathInProject: String
        ) : ToolArgs

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "readFileContent",
            description = "Reads the content of a file at the specified path",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "pathInProject",
                    description = "Path to the file to be created. The path is relative to the project root.",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun doExecute(args: Args): String {
            return fileOperationsTools.readFileContent(args.pathInProject)
        }
    }

    // Tool for appending text to a file
    class AppendToFileTool(private val fileOperationsTools: FileOperationsTools) : SimpleTool<AppendToFileTool.Args>() {
        @Serializable
        data class Args(
            val pathInProject: String,
            val text: String
        ) : ToolArgs

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "appendToFile",
            description = "Appends the provided text to an existing file",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "pathInProject",
                    description = "Path to the file to be created. The path is relative to the project root.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "text",
                    description = "Text content to append to the file",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun doExecute(args: Args): String {
            return fileOperationsTools.appendToFile(args.pathInProject, args.text)
        }
    }

    // Tool for replacing file content
    class ReplaceFileContentTool(private val fileOperationsTools: FileOperationsTools) : SimpleTool<ReplaceFileContentTool.Args>() {
        @Serializable
        data class Args(
            val pathInProject: String,
            val text: String
        ) : ToolArgs

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "replaceFileContent",
            description = "Replaces the content of a file at the specified path with the provided text",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "pathInProject",
                    description = "Path to the file to be created. The path is relative to the project root.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "text",
                    description = "Text content to replace the file with",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun doExecute(args: Args): String {
            return fileOperationsTools.replaceFileContent(args.pathInProject, args.text)
        }
    }

    fun createNewFileWithText(pathInProject: String, text: String): String {
        fileContentsByPath[pathInProject] = text
        return "OK"
    }

    fun readFileContent(pathInProject: String): String {
        return fileContentsByPath[pathInProject] ?: "File not found"
    }

    fun appendToFile(pathInProject: String, text: String): String {
        val existingContent = fileContentsByPath[pathInProject] ?: return "File not found"
        fileContentsByPath[pathInProject] = existingContent + text
        return "OK"
    }

    fun replaceFileContent(pathInProject: String, text: String): String {
        if (!fileContentsByPath.containsKey(pathInProject)) return "File not found"
        fileContentsByPath[pathInProject] = text
        return "OK"
    }

    fun asTools() = listOf(
        createNewFileWithTextTool,
        readFileContentTool,
        appendToFileTool,
        replaceFileContentTool
    )
}
