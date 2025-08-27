package io.github.aowubulao.simpleaireviewer.service

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import git4idea.repo.GitRepositoryManager

class CodeAnalyzer(private val project: Project) {

    private val logger = thisLogger()

    data class CodeChanges(
        val diff: String,
        val modifiedFiles: List<String>,
        val changesSummary: String
    )

    fun analyzeCurrentChanges(): CodeChanges? {
        try {
            val changeListManager = ChangeListManager.getInstance(project)
            val changes = changeListManager.defaultChangeList.changes

            if (changes.isEmpty()) {
                logger.info("No changes found in current changelist")
                return null
            }

            val diffBuilder = StringBuilder()
            val modifiedFiles = mutableListOf<String>()
            var addedLines = 0
            var deletedLines = 0

            for (change in changes) {
                val filePath = getFilePath(change)
                if (filePath != null) {
                    modifiedFiles.add(filePath)
                    val fileDiff = generateFileDiff(change, filePath)
                    diffBuilder.append(fileDiff)

                    val lines = fileDiff.lines()
                    addedLines += lines.count { it.startsWith("+") && !it.startsWith("+++") }
                    deletedLines += lines.count { it.startsWith("-") && !it.startsWith("---") }
                }
            }

            val changesSummary = buildChangesSummary(modifiedFiles.size, addedLines, deletedLines)

            return CodeChanges(
                diff = diffBuilder.toString(),
                modifiedFiles = modifiedFiles,
                changesSummary = changesSummary
            )

        } catch (e: Exception) {
            logger.error("Error analyzing code changes", e)
            return null
        }
    }

    private fun getFilePath(change: Change): String? {
        return when {
            change.afterRevision != null -> change.afterRevision!!.file.path
            change.beforeRevision != null -> change.beforeRevision!!.file.path
            else -> null
        }
    }

    private fun generateFileDiff(change: Change, filePath: String): String {
        val diffBuilder = StringBuilder()

        try {
            val beforeContent = change.beforeRevision?.content ?: ""
            val afterContent = change.afterRevision?.content ?: ""

            diffBuilder.append("--- a/$filePath\n")
            diffBuilder.append("+++ b/$filePath\n")

            if (change.beforeRevision == null) {
                // New file
                diffBuilder.append("@@ -0,0 +1,${afterContent.lines().size} @@\n")
                afterContent.lines().forEach { line ->
                    diffBuilder.append("+$line\n")
                }
            } else if (change.afterRevision == null) {
                // Deleted file
                diffBuilder.append("@@ -1,${beforeContent.lines().size} +0,0 @@\n")
                beforeContent.lines().forEach { line ->
                    diffBuilder.append("-$line\n")
                }
            } else {
                // Modified file - generate unified diff
                val diff = generateUnifiedDiff(beforeContent, afterContent)
                diffBuilder.append(diff)
            }

            diffBuilder.append("\n")

        } catch (e: Exception) {
            logger.error("Error generating diff for file: $filePath", e)
            diffBuilder.append("Error generating diff for $filePath: ${e.message}\n")
        }

        return diffBuilder.toString()
    }

    private fun generateUnifiedDiff(beforeContent: String, afterContent: String): String {
        val beforeLines = beforeContent.lines()
        val afterLines = afterContent.lines()

        // Simple diff implementation - in a real scenario, you might want to use a more sophisticated algorithm
        val diffBuilder = StringBuilder()

        val maxLines = maxOf(beforeLines.size, afterLines.size)
        var contextStart = 0
        var contextEnd = 0

        // Find changes
        val changes = mutableListOf<Triple<Int, String, String>>() // line number, type (-, +, space), content

        var beforeIndex = 0
        var afterIndex = 0

        while (beforeIndex < beforeLines.size || afterIndex < afterLines.size) {
            when {
                beforeIndex >= beforeLines.size -> {
                    // Only after lines left (additions)
                    changes.add(Triple(afterIndex, "+", afterLines[afterIndex]))
                    afterIndex++
                }

                afterIndex >= afterLines.size -> {
                    // Only before lines left (deletions)
                    changes.add(Triple(beforeIndex, "-", beforeLines[beforeIndex]))
                    beforeIndex++
                }

                beforeLines[beforeIndex] == afterLines[afterIndex] -> {
                    // Lines match
                    changes.add(Triple(beforeIndex, " ", beforeLines[beforeIndex]))
                    beforeIndex++
                    afterIndex++
                }

                else -> {
                    // Lines differ - mark as deletion and addition
                    changes.add(Triple(beforeIndex, "-", beforeLines[beforeIndex]))
                    changes.add(Triple(afterIndex, "+", afterLines[afterIndex]))
                    beforeIndex++
                    afterIndex++
                }
            }
        }

        // Group changes and add context
        if (changes.isNotEmpty()) {
            diffBuilder.append("@@ -1,${beforeLines.size} +1,${afterLines.size} @@\n")
            changes.forEach { (_, type, content) ->
                diffBuilder.append("$type$content\n")
            }
        }

        return diffBuilder.toString()
    }

    private fun buildChangesSummary(filesChanged: Int, addedLines: Int, deletedLines: Int): String {
        val summary = StringBuilder()
        summary.append("Changes Summary:\n")
        summary.append("- Files modified: $filesChanged\n")
        summary.append("- Lines added: $addedLines\n")
        summary.append("- Lines deleted: $deletedLines\n")
        summary.append("- Net change: ${addedLines - deletedLines} lines\n")
        return summary.toString()
    }

    fun getCurrentCommitMessage(): String? {
        return try {
            val changeListManager = ChangeListManager.getInstance(project)
            changeListManager.defaultChangeList.comment?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            logger.error("Error getting commit message", e)
            null
        }
    }

    fun getRepositoryInfo(): String? {
        return try {
            val repositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = repositoryManager.repositories

            if (repositories.isNotEmpty()) {
                val repo = repositories.first()
                val currentBranch = repo.currentBranchName ?: "unknown"
                "Repository: ${repo.root.name}, Branch: $currentBranch"
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Error getting repository info", e)
            null
        }
    }
}
