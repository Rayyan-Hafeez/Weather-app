package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class WeatherUiState {
    object Idle : WeatherUiState()
    object Loading : WeatherUiState()
    data class Success(
        val weatherData: WeatherData,
        val aiInsights: String,
        val isAiLoading: Boolean,
        val useFahrenheit: Boolean = false
    ) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("weather_app_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Persistent Alerts Opt-in Setup
    private val _alertsOptedIn = MutableStateFlow(prefs.getBoolean("alerts_opt_in", false))
    val alertsOptedIn: StateFlow<Boolean> = _alertsOptedIn.asStateFlow()

    // Persistent Saved Locations Setup
    private val _savedLocations = MutableStateFlow(
        prefs.getStringSet("saved_locations", setOf("Paris", "London", "Tokyo", "Cairo", "Sydney")) ?: setOf("Paris", "London", "Tokyo", "Cairo", "Sydney")
    )
    val savedLocations: StateFlow<Set<String>> = _savedLocations.asStateFlow()

    // Functional Bottom Navigation Tab State Setup
    private val _currentTab = MutableStateFlow("Home")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // Cache to maintain simulation state even if toggle elements are changed
    private var currentCityName: String = "Paris"
    private var lastConditionOverride: WeatherCondition? = null
    private var lastTempScale: Boolean = false

    init {
        createNotificationChannel()
        // Automatically load a default city on startup (e.g. "Paris")
        searchCity("Paris")
    }

    fun switchTab(tab: String) {
        _currentTab.value = tab
    }

    fun toggleAlertsOptIn() {
        val nextVal = !_alertsOptedIn.value
        _alertsOptedIn.value = nextVal
        prefs.edit().putBoolean("alerts_opt_in", nextVal).apply()
        
        if (nextVal) {
            showLocalAlert("Alerts Activated", "You will now receive high-priority severe updates for your saved locations.")
        }
    }

    fun addSavedLocation(city: String) {
        if (city.isBlank()) return
        val currentSet = _savedLocations.value.toMutableSet()
        val capitalized = city.trim().split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        if (currentSet.add(capitalized)) {
            _savedLocations.value = currentSet
            prefs.edit().putStringSet("saved_locations", currentSet).apply()
            
            if (_alertsOptedIn.value) {
                showLocalAlert("City Monitored", "$capitalized is now secured for real-time severe climate warnings.")
            }
        }
    }

    fun removeSavedLocation(city: String) {
        val currentSet = _savedLocations.value.toMutableSet()
        if (currentSet.remove(city)) {
            _savedLocations.value = currentSet
            prefs.edit().putStringSet("saved_locations", currentSet).apply()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun searchCity(cityName: String) {
        if (cityName.isBlank()) return
        currentCityName = cityName
        lastConditionOverride = null // reset simulation override when searching a new city
        
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            generateAndLoadWeather(cityName, null, lastTempScale)
        }
    }

    fun simulateWeather(condition: WeatherCondition) {
        val currentState = _uiState.value
        if (currentState is WeatherUiState.Success) {
            lastConditionOverride = condition
            viewModelScope.launch {
                _uiState.value = WeatherUiState.Loading
                generateAndLoadWeather(currentState.weatherData.cityName, condition, currentState.useFahrenheit)
            }
        }
    }

    fun toggleTemperatureScale() {
        val currentState = _uiState.value
        if (currentState is WeatherUiState.Success) {
            val newScale = !currentState.useFahrenheit
            lastTempScale = newScale
            _uiState.value = currentState.copy(useFahrenheit = newScale)
        }
    }

    // Geolocation detection coordinates input
    fun loadWeatherFromLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                val context = getApplication<Application>()
                val geocoder = Geocoder(context, Locale.getDefault())
                
                // Perform geocoding asynchronously on Dispatchers.IO
                val cityName = withContext(Dispatchers.IO) {
                    try {
                        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                        addresses?.firstOrNull()?.let { address ->
                            address.locality ?: address.subAdminArea ?: address.adminArea
                        }
                    } catch (e: Exception) {
                        null
                    }
                } ?: "Detected Location"
                
                _searchQuery.value = cityName
                currentCityName = cityName
                lastConditionOverride = null
                generateAndLoadWeather(cityName, null, lastTempScale)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error("An error occurred geolocating city: ${e.message}")
            }
        }
    }

    private suspend fun generateAndLoadWeather(cityName: String, forceCondition: WeatherCondition?, useFahrenheit: Boolean) {
        try {
            val simulatedWeather = generateSimulatedWeather(cityName, forceCondition)
            val isGeminiSupported = GeminiClient.isApiKeyAvailable()
            
            // Set success immediately with loading state for AI
            _uiState.value = WeatherUiState.Success(
                weatherData = simulatedWeather,
                aiInsights = "Asking AI Stylist...",
                isAiLoading = true,
                useFahrenheit = useFahrenheit
            )

            // Trigger severe weather push notification checking
            checkAndTriggerSevereAlert(simulatedWeather)

            // Asynchronously fetch AI content or use detailed local advisories
            viewModelScope.launch(Dispatchers.IO) {
                val insights = if (isGeminiSupported) {
                    GeminiClient.fetchWeatherInsights(
                        cityName = simulatedWeather.cityName,
                        condition = simulatedWeather.condition.displayName,
                        temp = simulatedWeather.temperature,
                        humidity = simulatedWeather.humidityPercent,
                        windSpeed = simulatedWeather.windSpeedKmh
                     )
                } else {
                    getLocalStylistRecommendation(simulatedWeather.condition, simulatedWeather.temperature)
                }

                // Push inside correct state
                val finalState = _uiState.value
                if (finalState is WeatherUiState.Success && finalState.weatherData.cityName == simulatedWeather.cityName) {
                    _uiState.value = finalState.copy(
                        aiInsights = insights,
                        isAiLoading = false
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.value = WeatherUiState.Error("An error occurred generating weather results: ${e.message}")
        }
    }

    private fun generateSimulatedWeather(cityName: String, forceCondition: WeatherCondition?): WeatherData {
        val cleanName = cityName.trim().lowercase()
        
        val condition = forceCondition ?: when {
            cleanName.contains("london") -> WeatherCondition.RAINY
            cleanName.contains("paris") -> WeatherCondition.PARTLY_CLOUDY
            cleanName.contains("cairo") || cleanName.contains("dubai") || cleanName.contains("delhi") -> WeatherCondition.SUNNY
            cleanName.contains("moscow") || cleanName.contains("reykjavik") || cleanName.contains("oslo") -> WeatherCondition.SNOWY
            cleanName.contains("chicago") || cleanName.contains("wellington") -> WeatherCondition.WINDY
            cleanName.contains("mumbai") || cleanName.contains("manila") || cleanName.contains("singapore") || cleanName.contains("tokyo") -> WeatherCondition.STORMY
            else -> {
                val hash = cleanName.fold(0) { acc, c -> acc + c.code }
                val index = Math.abs(hash) % WeatherCondition.values().size
                WeatherCondition.values()[index]
            }
        }

        // Set weather parameters
        val temp: Int
        val highTemp: Int
        val lowTemp: Int
        val windSpeed: Int
        val humidity: Int
        val uv: Int
        val pop: Int
        val aqi: Int

        when (condition) {
            WeatherCondition.SUNNY -> {
                val base = if (cleanName.contains("cairo") || cleanName.contains("dubai")) 39 else 28
                temp = base
                highTemp = base + 3
                lowTemp = base - 7
                windSpeed = 10
                humidity = 32
                uv = 9
                pop = 0
                aqi = 65
            }
            WeatherCondition.PARTLY_CLOUDY -> {
                temp = 22
                highTemp = 25
                lowTemp = 14
                windSpeed = 12
                humidity = 55
                uv = 5
                pop = 15
                aqi = 40
            }
            WeatherCondition.CLOUDY -> {
                temp = 16
                highTemp = 18
                lowTemp = 11
                windSpeed = 15
                humidity = 72
                uv = 3
                pop = 35
                aqi = 30
            }
            WeatherCondition.RAINY -> {
                temp = 12
                highTemp = 14
                lowTemp = 8
                windSpeed = 18
                humidity = 88
                uv = 2
                pop = 90
                aqi = 20
            }
            WeatherCondition.STORMY -> {
                temp = 25
                highTemp = 28
                lowTemp = 19
                windSpeed = 32
                humidity = 94
                uv = 1
                pop = 95
                aqi = 75
            }
            WeatherCondition.SNOWY -> {
                temp = -3
                highTemp = 1
                lowTemp = -8
                windSpeed = 20
                humidity = 78
                uv = 1
                pop = 85
                aqi = 15
            }
            WeatherCondition.WINDY -> {
                temp = 14
                highTemp = 17
                lowTemp = 9
                windSpeed = 46
                humidity = 48
                uv = 4
                pop = 10
                aqi = 25
            }
        }

        val desc = when (condition) {
            WeatherCondition.SUNNY -> "Clear, bright blue skies with glowing radiant sunshine."
            WeatherCondition.PARTLY_CLOUDY -> "Scattered cotton cloud drifts. Highly comfortable conditions."
            WeatherCondition.CLOUDY -> "Dull overcast layers wrapping the horizon. Ambient atmospheric light."
            WeatherCondition.RAINY -> "Active rain cooling the streets. A persistent refreshing drizzle."
            WeatherCondition.STORMY -> "Heavy localized downpours with distant structural thunder."
            WeatherCondition.SNOWY -> "Flurries pattern falling vertically. Crisp cold powder accumulations."
            WeatherCondition.WINDY -> "High pressure drafts sending debris rustling. Wind chill is present."
        }

        // Generate hourly list starting at current simulated hour - Extended to 48 hours (16 points * 3-hour chunks)
        val currentHour = 18
        val hourlyList = (0 until 16).map { idx ->
            val hourVal = (currentHour + idx * 3) % 24
            val amPm = if (hourVal >= 12) "PM" else "AM"
            val displayHour = when {
                hourVal == 0 -> 12
                hourVal > 12 -> hourVal - 12
                else -> hourVal
            }
            
            val hourDiff = when (idx % 8) {
                0 -> -2
                1 -> 2
                2 -> 4 // Peak high
                3 -> 1
                4 -> -2
                5 -> -5
                6 -> -6
                else -> -4
            }
            
            HourlyForecast(
                time = "$displayHour $amPm",
                temp = temp + hourDiff,
                condition = if (idx in 1..3 && condition == WeatherCondition.PARTLY_CLOUDY) WeatherCondition.SUNNY else condition,
                popPercent = if (condition == WeatherCondition.RAINY || condition == WeatherCondition.STORMY) Math.min(100, pop + idx) else Math.max(0, pop - idx * 2)
            )
        }

        // Generate 10-day daily schedule forecast starting with Today and Tomorrow
        val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val currentDayIndex = 3 // Thursday (21 May 2026)
        
        val dailyList = (0 until 10).map { idx ->
            val dayIdx = (currentDayIndex + idx) % 7
            val label = when (idx) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> daysOfWeek[dayIdx]
            }
            val dateLabel = "$label ${21 + idx}"
            
            val dayDiffMax = (idx - 4) * 1
            val dayDiffMin = (idx - 4) * -1
            
            // Fluctuating daily conditions for variety
            val dayCond = when {
                idx == 2 && condition == WeatherCondition.RAINY -> WeatherCondition.PARTLY_CLOUDY
                idx == 4 && condition == WeatherCondition.SUNNY -> WeatherCondition.PARTLY_CLOUDY
                idx == 5 && condition == WeatherCondition.STORMY -> WeatherCondition.RAINY
                idx == 7 && condition == WeatherCondition.SNOWY -> WeatherCondition.PARTLY_CLOUDY
                else -> condition
            }

            DailyForecast(
                day = dateLabel,
                condition = dayCond,
                maxTemp = highTemp + dayDiffMax,
                minTemp = lowTemp + dayDiffMin,
                description = when (dayCond) {
                    WeatherCondition.SUNNY -> "Clear and sunny skies"
                    WeatherCondition.PARTLY_CLOUDY -> "Light cloud intervals"
                    WeatherCondition.CLOUDY -> "Gloomy overcast skies"
                    WeatherCondition.RAINY -> "Frequent showers likely"
                    WeatherCondition.STORMY -> "Isolated afternoon storms"
                    WeatherCondition.SNOWY -> "Moderate snow accumulations"
                    WeatherCondition.WINDY -> "Sustained blustery corridors"
                }
            )
        }

        // Convert search query text to properly spaced title
        val formattedCity = cityName.trim().split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        return WeatherData(
            cityName = formattedCity,
            country = when {
                cleanName.contains("london") -> "United Kingdom"
                cleanName.contains("paris") -> "France"
                cleanName.contains("cairo") -> "Egypt"
                cleanName.contains("dubai") -> "UAE"
                cleanName.contains("delhi") -> "India"
                cleanName.contains("moscow") -> "Russia"
                cleanName.contains("oslo") -> "Norway"
                cleanName.contains("tokyo") -> "Japan"
                cleanName.contains("sydney") -> "Australia"
                cleanName.contains("chicago") -> "United States"
                else -> "Regional Area"
            },
            condition = condition,
            temperature = temp,
            highTemp = highTemp,
            lowTemp = lowTemp,
            description = desc,
            localTime = "18:19 PM",
            windSpeedKmh = windSpeed,
            humidityPercent = humidity,
            uvIndex = uv,
            prepProbability = pop,
            visibilityKm = if (condition == WeatherCondition.SNOWY || condition == WeatherCondition.STORMY) 5 else 12,
            aqi = aqi,
            pressureHpa = 1014,
            hourlyForecasts = hourlyList,
            dailyForecasts = dailyList
        )
    }

    private fun getLocalStylistRecommendation(condition: WeatherCondition, temp: Int): String {
        return when (condition) {
            WeatherCondition.SUNNY -> "Stylist Recommendation: It's warm ($temp°C) and exceptionally sunny! Suit up in comfortable linen fabrics, elegant shades, and a sunhat. A perfect afternoon for a refreshing run or a light picnic in the green belt!"
            WeatherCondition.PARTLY_CLOUDY -> "Stylist Recommendation: Overcast intervals make it a gorgeous $temp°C. Perfect for modern light chinos paired with a textured long-sleeve knit. Ideal day for jogging or long sidewalk walks."
            WeatherCondition.CLOUDY -> "Stylist Recommendation: Soft clouds blocking heavy UV at $temp°C. A comfortable waffle-knit crewneck or a structured denim jacket is perfect. Outstanding weather for city exploration or visiting gallery cafes!"
            WeatherCondition.RAINY -> "Stylist Recommendation: Rain alert! Slip on a water-repellent trench coat, waterproof utility boots, and keep a clean matte black umbrella close. Perfect evening to explore a local bookstore."
            WeatherCondition.STORMY -> "Stylist Recommendation: Storm weather! Keep cozy inside in organic fleece sweatpants, thick cashmere socks, and a heavy oversized knit. Great afternoon to brew loose-leaf tea and read."
            WeatherCondition.SNOWY -> "Stylist Recommendation: Subzero magical conditions ($temp°C)! Bundle up in a heavy insulated puffer jacket, fleece-lined cargo pants, a heavy wool scarf, and warm gloves. Great day to shoot street photography."
            WeatherCondition.WINDY -> "Stylist Recommendation: Blustery winds at $temp°C. Wear an athletic windbreaker, high-top trainers, and keep your hair styled or secure under a fitted cap. Perfect weather to fly a kite or watch waves."
        }
    }

    // Checking of weather signals & automated triggering of local alerts for Saved Locations
    private fun checkAndTriggerSevereAlert(weather: WeatherData) {
        if (!_alertsOptedIn.value) return
        
        val isCitySaved = _savedLocations.value.any { it.equals(weather.cityName, ignoreCase = true) }
        if (!isCitySaved && !weather.cityName.equals(currentCityName, ignoreCase = true)) return

        val title: String
        val msg: String
        
        when {
            weather.condition == WeatherCondition.STORMY -> {
                title = "⛈️ Severe Storm Warning!"
                msg = "${weather.cityName} is expecting intense thunder flashes, high precipitation, and gusty winds."
            }
            weather.condition == WeatherCondition.SNOWY && weather.temperature <= -2 -> {
                title = "❄️ Blizzard Advisory"
                msg = "Subzero blizzard accumulation active in ${weather.cityName}. Drive carefully."
            }
            weather.condition == WeatherCondition.WINDY && weather.windSpeedKmh >= 40 -> {
                title = "💨 High Gale Warning"
                msg = "Extreme pressure drafts up to ${weather.windSpeedKmh} km/h detected in ${weather.cityName}."
            }
            weather.temperature >= 38 -> {
                title = "🚨 Extreme Heat Alert"
                msg = "A severe heat dome is active in ${weather.cityName} at ${weather.temperature}°C. Keep hydrated."
            }
            weather.temperature <= -5 -> {
                title = "🥶 Hard Freeze Alert"
                msg = "Extreme hard freeze conditions (${weather.temperature}°C) active in ${weather.cityName}."
            }
            else -> return
        }

        showLocalAlert(title, msg)
    }

    // Creating Native Notification Channel (Oreo+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Severe Climate Updates"
            val desc = "Dynamic alert banners notifying users of extreme winds, blizzard freezes or high thunderstorms."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("WEATHER_ALERTS", name, importance).apply {
                description = desc
            }
            val notificationManager: NotificationManager =
                getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Firing a true Android local notification banner
    fun showLocalAlert(title: String, message: String) {
        val context = getApplication<Application>()
        val builder = NotificationCompat.Builder(context, "WEATHER_ALERTS")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33) {
                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            } else {
                Log.w("WeatherViewModel", "Permission ACCESS_NOTIFICATION not found, skipping Android system banner.")
            }
        } catch (e: SecurityException) {
            Log.e("WeatherViewModel", "Security exception firing notification: ${e.message}")
        }
    }

    // Explicit Demonstration Alert for severe storm conditions
    fun triggerDemoAlert() {
        showLocalAlert("⛈️ Severe Storm Warning (Demo)", "Extreme meteorological draft alerts successfully verified! Dynamic advice updates automatically.")
    }
}
