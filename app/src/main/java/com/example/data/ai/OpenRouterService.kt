package com.example.data.ai

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class OpenRouterRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int? = 1200
)

data class Message(
    val role: String,
    val content: String
)

data class OpenRouterResponse(
    val choices: List<Choice>?
)

data class Choice(
    val message: Message?
)

interface OpenRouterService {
    @POST("api/v1/chat/completions")
    suspend fun generateCards(
        @Header("Authorization") token: String,
        @Header("HTTP-Referer") referer: String = "https://flashtonnos.app",
        @Header("X-Title") title: String = "FlashTonnos",
        @Body request: OpenRouterRequest
    ): OpenRouterResponse
}
