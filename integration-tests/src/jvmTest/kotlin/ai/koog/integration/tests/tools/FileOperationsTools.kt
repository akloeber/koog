package ai.koog.integration.tests.tools

import ai.koog.agents.core.tools.SimpleTool
import kotlinx.serialization.Serializable

class FileOperationsTools {
    val fileContentsByPath = mutableMapOf<String, String>()

    val createNewFileWithTextTool = CreateNewFileWithText(this)
    val readFileContentTool = ReadFileContent(this)
    val appendToFileTool = AppendToFile(this)
    val replaceFileContentTool = ReplaceFileContent(this)

    // Tool for creating a new file with text content
    class CreateNewFileWithText(private val fileOperationsTools: FileOperationsTools) : SimpleTool<CreateNewFileWithText.Args>() {
        @Serializable
        data class Args(
            val pathInProject: String,
            val text: String
        )

        override val argsSerializer = Args.serializer()

        override val description = "Creates a new file at the specified path with the provided text content"

        override suspend fun doExecute(args: Args): String {
            return fileOperationsTools.createNewFileWithText(args.pathInProject, args.text)
        }
    }

    // Tool for reading file content
    class ReadFileContent(private val fileOperationsTools: FileOperationsTools) : SimpleTool<ReadFileContent.Args>() {
        @Serializable
        data class Args(
            val pathInProject: String
        )

        override val argsSerializer = Args.serializer()

        override val description = "Reads the content of a file at the specified path"

        override suspend fun doExecute(args: Args): String {
            return fileOperationsTools.readFileContent(args.pathInProject)
        }
    }

    // Tool for appending text to a file
    class AppendToFile(private val fileOperationsTools: FileOperationsTools) : SimpleTool<AppendToFile.Args>() {
        @Serializable
        data class Args(
            val pathInProject: String,
            val text: String
        )

        override val argsSerializer = Args.serializer()

        override val description = "Appends the provided text to an existing file"

        override suspend fun doExecute(args: Args): String {
            return fileOperationsTools.appendToFile(args.pathInProject, args.text)
        }
    }

    // Tool for replacing file content
    class ReplaceFileContent(private val fileOperationsTools: FileOperationsTools) : SimpleTool<ReplaceFileContent.Args>() {
        @Serializable
        data class Args(
            val pathInProject: String,
            val text: String
        )

        override val argsSerializer = Args.serializer()

        override val description = "Replaces the content of a file at the specified path with the provided text"

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
