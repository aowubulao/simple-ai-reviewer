package io.github.aowubulao.simpleaireviewer.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class AIReviewerConfigurable : Configurable {

    private val settings = AIReviewerSettings.getInstance()

    private val apiUrlField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val modelField = JBTextField()
    private val temperatureSpinner = JSpinner(SpinnerNumberModel(30, 0, 100, 1)) // 0-100, will be divided by 100
    private val systemPromptArea = JBTextArea()
    private val enabledCheckBox = JBCheckBox("Enable AI Code Review")
    private val reportLanguage = ComboBox(
        arrayOf(
            "简体中文",
            "English",
            "日本語"
        )
    )

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "AI Code Reviewer"

    override fun createComponent(): JComponent {
        panel = panel {
            group("API Configuration") {
                row("API URL:") {
                    cell(apiUrlField)
                        .columns(80)
                        .comment("Enter the API endpoint URL (e.g., https://api.openai.com/v1/chat/completions)")
                }
                row("API Key:") {
                    cell(apiKeyField)
                        .columns(80)
                        .comment("Enter your API key for authentication")
                }
                row("Model:") {
                    cell(modelField)
                        .columns(80)
                        .comment("Enter the AI model name to use for code review (e.g., gpt-4, claude-3-sonnet, deepseek-chat)")
                }
                row("Report Language:") {
                    cell(reportLanguage)
                        .comment("Select language")
                }
                row("Temperature:") {
                    cell(temperatureSpinner)
                        .comment("Creativity level (0-100, lower = more focused)")
                }
            }

            group("Review Configuration") {
                row {
                    cell(enabledCheckBox)
                }
                row("System Prompt:") {
                    scrollCell(systemPromptArea)
                        .columns(70)
                        .rows(30)
                        .comment("Customize the instructions given to the AI reviewer")
                }
            }
        }

        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = settings.state
        return apiUrlField.text != state.apiUrl ||
                String(apiKeyField.password) != state.apiKey ||
                modelField.text != state.model ||
                (temperatureSpinner.value as Int) != (state.temperature * 100).toInt() ||
                systemPromptArea.text != state.systemPrompt ||
                enabledCheckBox.isSelected != state.enabled ||
                (reportLanguage.selectedItem as String) != state.reportLanguage

    }

    override fun apply() {
        val state = settings.state
        state.apiUrl = apiUrlField.text.trim()
        state.apiKey = String(apiKeyField.password)
        state.model = modelField.text.trim()
        state.temperature = (temperatureSpinner.value as Int) / 100.0
        state.systemPrompt = systemPromptArea.text
        state.enabled = enabledCheckBox.isSelected
        state.reportLanguage = reportLanguage.selectedItem as String
    }

    override fun reset() {
        val state = settings.state
        apiUrlField.text = state.apiUrl
        apiKeyField.text = state.apiKey
        modelField.text = state.model
        temperatureSpinner.value = (state.temperature * 100).toInt()
        systemPromptArea.text = state.systemPrompt
        enabledCheckBox.isSelected = state.enabled
        reportLanguage.selectedItem = state.reportLanguage

        systemPromptArea.preferredSize = Dimension(800, 800)
        panel?.preferredSize = Dimension(1000, 1000)
    }
}
