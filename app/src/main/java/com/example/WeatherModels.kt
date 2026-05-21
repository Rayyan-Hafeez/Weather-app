package com.example

import androidx.compose.ui.graphics.Color

enum class WeatherCondition(val displayName: String) {
    SUNNY("Sunny"),
    PARTLY_CLOUDY("Partly Cloudy"),
    CLOUDY("Cloudy"),
    RAINY("Rainy"),
    STORMY("Thunderstorm"),
    SNOWY("Snowy"),
    WINDY("Windy");

    // Returns a beautiful gradient colors representing each weather condition
    fun getThemeGradient(): List<Color> {
        return when (this) {
            SUNNY -> listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF3B82F6), Color(0xFFFBBF24))
            PARTLY_CLOUDY -> listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF2563EB), Color(0xFF93C5FD))
            CLOUDY -> listOf(Color(0xFF0F172A), Color(0xFF334155), Color(0xFF475569), Color(0xFF94A3B8))
            RAINY -> listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F766E), Color(0xFF0D9488))
            STORMY -> listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF311042), Color(0xFF4A148C))
            SNOWY -> listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0369A1), Color(0xFF38BDF8))
            WINDY -> listOf(Color(0xFF0F172A), Color(0xFF0F172A), Color(0xFF0369A1), Color(0xFF0D9488))
        }
    }
}

data class WeatherData(
    val cityName: String,
    val country: String,
    val condition: WeatherCondition,
    val temperature: Int,
    val highTemp: Int,
    val lowTemp: Int,
    val description: String,
    val localTime: String,
    val windSpeedKmh: Int,
    val humidityPercent: Int,
    val uvIndex: Int,
    val prepProbability: Int,
    val visibilityKm: Int,
    val aqi: Int, // Air Quality Index (1 to 500)
    val pressureHpa: Int,
    val hourlyForecasts: List<HourlyForecast>,
    val dailyForecasts: List<DailyForecast>
)

data class HourlyForecast(
    val time: String,
    val temp: Int,
    val condition: WeatherCondition,
    val popPercent: Int // Probability of prep (%)
)

data class DailyForecast(
    val day: String,
    val condition: WeatherCondition,
    val maxTemp: Int,
    val minTemp: Int,
    val description: String
)
