package io.github.aowubulao.simpleaireviewer.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import io.github.aowubulao.simpleaireviewer.service.AIReviewService
import io.github.aowubulao.simpleaireviewer.service.CodeAnalyzer
import io.github.aowubulao.simpleaireviewer.service.ReviewReportGenerator
import io.github.aowubulao.simpleaireviewer.settings.AIReviewerSettings

class AICodeReviewAction : AnAction() {

    private val logger = thisLogger()
    private val title = "AI Code Review";
    private val errorTitle = "AI Code Review ERROR";

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val changeListManager = ChangeListManager.getInstance(project)
        if (changeListManager.defaultChangeList.changes.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No changes found in the current changelist.",
                title
            )
            return
        }

        val settings = AIReviewerSettings.getInstance().state
        if (!settings.enabled) {
            Messages.showWarningDialog(
                project,
                "AI Code Review is disabled. Please enable it in Settings > Tools > AI Code Reviewer.",
                title
            )
            return
        }

        if (settings.apiKey.isBlank()) {
            Messages.showWarningDialog(
                project,
                "API key is not configured. Please set it in Settings > Tools > AI Code Reviewer.",
                title
            )
            return
        }

        // 确认
        val result = Messages.showYesNoDialog(
            project,
            "This will analyze your current changes and generate an AI code review.\n\n" +
                    "The review will be generated using the configured AI model and may take a few moments.\n\n" +
                    "Do you want to continue?",
            title,
            Messages.getQuestionIcon()
        )

        if (result != Messages.YES) {
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating AI Code Review", true) {
            override fun run(indicator: ProgressIndicator) {
                performReview(project, indicator)
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation

        if (project == null) {
            presentation.isEnabled = false
            return
        }

        val changeListManager = ChangeListManager.getInstance(project)
        presentation.isEnabled = changeListManager.defaultChangeList.changes.isNotEmpty()

        val settings = AIReviewerSettings.getInstance().state
        if (!settings.enabled) {
            presentation.text = "AI Code Review (Disabled)"
        } else if (settings.apiKey.isBlank()) {
            presentation.text = "AI Code Review (Not Configured)"
        } else {
            presentation.text = title
        }
    }

    private fun performReview(project: Project, indicator: ProgressIndicator) {
        try {
            indicator.text = "Analyzing code changes..."
            indicator.fraction = 0.1

            val codeAnalyzer = CodeAnalyzer(project)
            val codeChanges = codeAnalyzer.analyzeCurrentChanges()

            if (codeChanges == null) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Failed to analyze code changes. Please ensure you have changes in your current changelist.",
                        errorTitle
                    )
                }
                return
            }

            indicator.text = "Preparing review request..."
            indicator.fraction = 0.3

            val commitMessage = codeAnalyzer.getCurrentCommitMessage()
            val repositoryInfo = codeAnalyzer.getRepositoryInfo()

            val reviewRequest = AIReviewService.ReviewRequest(
                codeChanges = codeChanges.diff,
                commitMessage = commitMessage,
                filePaths = codeChanges.modifiedFiles
            )

            indicator.text = "Calling AI service..."
            indicator.fraction = 0.5

            val aiService = AIReviewService.getInstance()
            val reviewResponse = aiService.generateReview(reviewRequest).get()

            indicator.text = "Generating report..."
            indicator.fraction = 0.8

            if (!reviewResponse.success) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Failed to generate AI review: ${reviewResponse.error}",
                        errorTitle
                    )
                }
                return
            }

            val reportGenerator = ReviewReportGenerator(project)
            val report = reportGenerator.generateReport(
                aiReview = reviewResponse.review!!,
                codeChanges = codeChanges,
                commitMessage = commitMessage,
                repositoryInfo = repositoryInfo
            )

            indicator.text = "Saving report..."
            indicator.fraction = 0.9

            ApplicationManager.getApplication().invokeLater {
                reportGenerator.saveReportToFile(report)

                reportGenerator.saveHtmlReportToFile(report)

                Messages.showInfoMessage(
                    project,
                    "AI code review completed successfully!\n\n" +
                            "Summary: ${report.summary}\n\n" +
                            "The detailed review has been saved to a markdown file and opened in the editor.",
                    "AI Code Review Complete"
                )
            }

            indicator.fraction = 1.0

        } catch (e: Exception) {
            logger.error("Error performing AI code review", e)
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(
                    project,
                    "An unexpected error occurred during the review: ${e.message}",
                    errorTitle
                )
            }
        }
    }
}
