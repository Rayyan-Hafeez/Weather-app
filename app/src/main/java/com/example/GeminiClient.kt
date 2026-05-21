package com.example

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// request structures
data class GeminiPart(val text: String? = null)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

// response structures
data class GeminiCandidate(val content: GeminiContent?)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Checks if the configured Gemini api key is a real key or a placeholder.
     */
    fun isApiKeyAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && 
               key != "MY_GEMINI_API_KEY" && 
               key != "YOUR_GEMINI_API_KEY" && 
               !key.contains("PLACEHOLDER")
    }

    /**
     * Calls Gemini 3.5 Flash to generate weather insights.
     */
    suspend fun fetchWeatherInsights(cityName: String, condition: String, temp: Int, humidity: Int, windSpeed: Int): String {
        if (!isApiKeyAvailable()) {
            return "API Key is not configured. Add your GEMINI_API_KEY to AI Studio Secrets to unlock real-time Gemini AI meteorologist & stylist insights."
        }

        val prompt = "Provide a high-quality, friendly 2-3 sentence personalized weather summary for $cityName. The current weather is $condition at LATEST temp of $temp°C, with $humidity% humidity and $windSpeed km/h wind. Recommend specifically what type of clothes/layers (with style accessories like sunglasses, hats, etc.) to wear, and suggest 1-2 outdoor or indoor activities suited for these specific local conditions today. Keep it short, cohesive, and styling-focused without any markdown formatting."
        
        val systemLine = "You are a warm, helpful AI meteorologist and expert personal fashion stylist. Recommend real visual fashion ideas for today's weather."

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemLine)))
        )

        return try {
            val response = service.generateContent(BuildConfig.GEMINI_API_KEY, request)
            Log.d(TAG, "Response: $response")
            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            rawText?.trim() ?: "No response from Gemini AI. Please try again."
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching insights", e)
            "Could not connect to Gemini AI. Technical details: ${e.message}"
        }
    }
}
