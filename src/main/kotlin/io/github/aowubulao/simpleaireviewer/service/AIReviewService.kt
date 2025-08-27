package io.github.aowubulao.simpleaireviewer.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import io.github.aowubulao.simpleaireviewer.settings.AIReviewerSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.CompletableFuture

@Service
class AIReviewService {

    private val logger = thisLogger()
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            logger.info("Making request to: ${request.url}")
            chain.proceed(request)
        }
        .connectTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    data class ReviewRequest(
        val codeChanges: String,
        val commitMessage: String? = null,
        val filePaths: List<String> = emptyList()
    )

    data class ReviewResponse(
        val success: Boolean,
        val review: String? = null,
        val error: String? = null
    )

    fun generateReview(request: ReviewRequest): CompletableFuture<ReviewResponse> {
        val future = CompletableFuture<ReviewResponse>()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val settings = AIReviewerSettings.getInstance().state

                if (!settings.enabled) {
                    future.complete(ReviewResponse(false, error = "AI Code Review is disabled"))
                    return@executeOnPooledThread
                }

                if (settings.apiKey.isBlank()) {
                    future.complete(ReviewResponse(false, error = "API key is not configured"))
                    return@executeOnPooledThread
                }

                val prompt = buildPrompt(request)
                val response = callAI(settings, prompt)

                future.complete(response)

            } catch (e: Exception) {
                logger.error("Error generating AI review", e)
                future.complete(ReviewResponse(false, error = "Failed to generate review: ${e.message}"))
            }
        }

        return future
    }

    private fun buildPrompt(request: ReviewRequest): String {
        val prompt = StringBuilder()

        prompt.append("Code Review Request\n\n")

        if (request.filePaths.isNotEmpty()) {
            prompt.append("Modified Files:\n")
            request.filePaths.forEach { path ->
                prompt.append("- $path\n")
            }
            prompt.append("\n")
        }

        prompt.append("Code Changes:\n```diff\n")
        prompt.append(request.codeChanges)
        prompt.append("\n```\n\n")

        prompt.append("Please provide a comprehensive code review following the system instructions.")

        return prompt.toString()
    }

    private fun callAI(settings: AIReviewerSettings.State, prompt: String): ReviewResponse {
        try {
            val requestBody = buildRequestBody(settings, prompt)

            val request = Request.Builder()
                .url(settings.apiUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    logger.error("API request failed: ${response.code} - $errorBody")
                    return ReviewResponse(false, error = "API request failed: ${response.code}")
                }

                val responseBody = response.body?.string()
                    ?: return ReviewResponse(false, error = "Empty response from API")

                return parseResponse(responseBody)
            }

        } catch (e: IOException) {
            logger.error("Network error calling AI API", e)
            return ReviewResponse(false, error = "Network error: ${e.message}")
        } catch (e: Exception) {
            logger.error("Unexpected error calling AI API", e)
            return ReviewResponse(false, error = "Unexpected error: ${e.message}")
        }
    }

    private fun buildRequestBody(settings: AIReviewerSettings.State, prompt: String): RequestBody {

        val jsonBody = JsonObject().apply {
            addProperty("model", settings.model)
            addProperty("temperature", settings.temperature)

            val messages = com.google.gson.JsonArray().apply {
                // System message
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", buildSystemPrompt(settings))
                })

                // User message
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", prompt)
                })
            }
            add("messages", messages)
        }

        return jsonBody.toString().toRequestBody("application/json".toMediaType())
    }

    private fun buildSystemPrompt(settings: AIReviewerSettings.State): String {
        return settings.systemPrompt + "\n report use language is: \"" + settings.reportLanguage + "\"";
    }

    private fun parseResponse(responseBody: String): ReviewResponse {
        try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)

            val choices = jsonResponse.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                return ReviewResponse(false, error = "No response choices found")
            }

            val firstChoice = choices[0].asJsonObject
            val message = firstChoice.getAsJsonObject("message")
            val content = message.get("content")?.asString

            if (content.isNullOrBlank()) {
                return ReviewResponse(false, error = "Empty response content")
            }

            return ReviewResponse(true, review = content)

        } catch (e: Exception) {
            logger.error("Error parsing AI response", e)
            return ReviewResponse(false, error = "Failed to parse response: ${e.message}")
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): AIReviewService {
            return ApplicationManager.getApplication().getService(AIReviewService::class.java)
        }
    }
}
