package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DailyForecast
import com.example.HourlyForecast
import com.example.WeatherCondition
import com.example.WeatherData
import com.example.WeatherUiState
import com.example.WeatherViewModel
import com.google.android.gms.location.LocationServices
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val alertsOptedIn by viewModel.alertsOptedIn.collectAsState()
    val savedLocations by viewModel.savedLocations.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // ACCESS_FINE_LOCATION permissions launcher contract
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            try {
                LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        viewModel.loadWeatherFromLocation(loc.latitude, loc.longitude)
                    } else {
                        viewModel.showLocalAlert("GPS Coordination Alert", "Coordinated station not found in cache. Accessing simulated Rome parameters.")
                        viewModel.loadWeatherFromLocation(41.8902, 12.4922)
                    }
                }
            } catch (e: SecurityException) {
                Log.e("WeatherScreen", "Location security error: ${e.message}")
            }
        } else {
            viewModel.showLocalAlert("Permission Required", "GPS tracker declined. Let's type target location manually.")
        }
    }

    // Background gradient linked directly to current state with luscious Frosted Glass sky gradients
    val backgroundBrush = when (val state = uiState) {
        is WeatherUiState.Success -> {
            val conditionColors = when (state.weatherData.condition) {
                WeatherCondition.SUNNY -> listOf(Color(0xFF4A90E2), Color(0xFF356DB2), Color(0xFF1D2B64))
                WeatherCondition.PARTLY_CLOUDY -> listOf(Color(0xFF5A9FE6), Color(0xFF34495E), Color(0xFF1D2B64))
                WeatherCondition.CLOUDY -> listOf(Color(0xFF4B79A1), Color(0xFF283E51), Color(0xFF1A263F))
                WeatherCondition.RAINY -> listOf(Color(0xFF3A7BD5), Color(0xFF3A6073), Color(0xFF1D233F))
                WeatherCondition.STORMY -> listOf(Color(0xFF2C3E50), Color(0xFF1F1C2C), Color(0xFF0F0E1E))
                WeatherCondition.SNOWY -> listOf(Color(0xFF83A4D4), Color(0xFF4B6CB7), Color(0xFF1E2F5A))
                WeatherCondition.WINDY -> listOf(Color(0xFF3E8CDC), Color(0xFF5A75A4), Color(0xFF1D2B64))
            }
            Brush.verticalGradient(colors = conditionColors)
        }
        else -> Brush.verticalGradient(
            colors = listOf(Color(0xFF4A90E2), Color(0xFF1D2B64)),
            startY = 0f
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Search Row & metrics is present dynamically
            SearchAndScaleHeader(
                searchQuery = searchQuery,
                onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                onSearchTriggered = {
                    viewModel.searchCity(searchQuery)
                    focusManager.clearFocus()
                },
                useFahrenheit = (uiState as? WeatherUiState.Success)?.useFahrenheit ?: false,
                onToggleDegrees = { viewModel.toggleTemperatureScale() },
                onGpsTriggered = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    viewModel.loadWeatherFromLocation(loc.latitude, loc.longitude)
                                } else {
                                    viewModel.showLocalAlert("GPS Coordination Alert", "Coordinated device spot empty. Sourcing matching simulated Rome climate.")
                                    viewModel.loadWeatherFromLocation(41.8902, 12.4922)
                                }
                            }
                        } catch (e: SecurityException) {
                            Log.e("WeatherScreen", "Location security error: ${e.message}")
                        }
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Popular preset pills for instantaneous demonstration
            QuickCityNavigator(
                onCitySelected = { city ->
                    viewModel.onSearchQueryChanged(city)
                    viewModel.searchCity(city)
                    focusManager.clearFocus()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tab-aware layouts routing switcher
            when (currentTab) {
                "Home" -> {
                    when (val state = uiState) {
                        is WeatherUiState.Idle -> {
                            EmptyStateCard(message = "Search for a city above or tap the GPS locator to load local atmospheric parameters.")
                        }
                        is WeatherUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                        is WeatherUiState.Success -> {
                            WeatherDetailsContent(
                                state = state,
                                viewModel = viewModel
                            )
                        }
                        is WeatherUiState.Error -> {
                            ErrorStateCard(
                                message = state.message,
                                onRetry = { viewModel.searchCity("Paris") }
                            )
                        }
                    }
                }
                "Radar" -> {
                    RadarTabScreen()
                }
                "Almanac" -> {
                    when (val state = uiState) {
                        is WeatherUiState.Success -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                WeeklyAlmanacSection(
                                    dailyList = state.weatherData.dailyForecasts,
                                    convertToDegrees = { temp ->
                                        val converted = if (state.useFahrenheit) (temp * 9 / 5) + 32 else temp
                                        "$converted°"
                                    }
                                )
                            }
                        }
                        else -> {
                            EmptyStateCard(message = "Search and load a city first to browse its extended 10-day climate almanac records.")
                        }
                    }
                }
                "Settings" -> {
                    SettingsTabScreen(
                        viewModel = viewModel,
                        alertsOptedIn = alertsOptedIn,
                        savedLocations = savedLocations
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))

            // Premium simulation of Bottom Navigation with glass panels
            FrostedBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { viewModel.switchTab(it) }
            )
            
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
fun SearchAndScaleHeader(
    searchQuery: String,
    onQueryChanged: (String) -> Unit,
    onSearchTriggered: () -> Unit,
    useFahrenheit: Boolean,
    onToggleDegrees: () -> Unit,
    onGpsTriggered: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_and_scale_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChanged,
            placeholder = { Text("Search City...", color = Color.White.copy(alpha = 0.6f)) },
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .testTag("city_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Icon",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchTriggered() })
        )

        Spacer(modifier = Modifier.width(8.dp))

        // GPS detector button
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .clickable { onGpsTriggered() }
                .testTag("gps_location_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Detect Current Location via GPS",
                tint = Color(0xFFFCA5A5),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Toggle scale Celsius / Fahrenheit
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .clickable { onToggleDegrees() }
                .testTag("temperature_scale_toggle"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (useFahrenheit) "°F" else "°C",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun QuickCityNavigator(onCitySelected: (String) -> Unit) {
    val cities = listOf("Paris", "London", "Tokyo", "Cairo", "Sydney", "New York", "Moscow")
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(cities) { city ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    .clickable { onCitySelected(city) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("preset_$city")
            ) {
                Text(
                    text = city,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun WeatherDetailsContent(
    state: WeatherUiState.Success,
    viewModel: WeatherViewModel
) {
    val data = state.weatherData
    val useFahrenheit = state.useFahrenheit

    // Converter helpers
    fun Int.tempString(): String {
        val converted = if (useFahrenheit) (this * 9 / 5) + 32 else this
        return "$converted°"
    }

    // Dynamic Weather Main Card with Frosted Glass aesthetic styling
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("current_station_display"),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location Pin",
                    tint = Color(0xFFFCA5A5),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${data.cityName}",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "Monday, 12 June • ${data.localTime}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Giant Custom Vector Graphics Illustration with Abstract Mesh Glow overlay
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .drawBehind {
                        // Soft shimmering abstract mesh glow behind the icon
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                                radius = size.width * 0.7f,
                                center = Offset(size.width / 2f, size.height / 2f)
                            ),
                            radius = size.width * 0.7f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                CustomWeatherGraphics(
                    condition = data.condition,
                    modifier = Modifier
                        .size(130.dp)
                        .testTag("condition_graphics_${data.condition.name.lowercase()}")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Giant temperature layout from the design HTML spec
            val tempVal = data.temperature.let { 
                if (useFahrenheit) (it * 9 / 5) + 32 else it 
            }
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.testTag("station_temperature_row")
            ) {
                Text(
                    text = "$tempVal",
                    color = Color.White,
                    fontSize = 100.sp,
                    lineHeight = 100.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.testTag("station_temperature")
                )
                Text(
                    text = "°",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    text = if (useFahrenheit) "F" else "C",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            Text(
                text = data.condition.displayName,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = data.description,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "High: ${data.highTemp.tempString()}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "•",
                    color = Color.White.copy(alpha = 0.4f)
                )
                Text(
                    text = "Low: ${data.lowTemp.tempString()}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // Interactive Atmospheric Simulator Controls
    AtmosphereSandboxPanel(
        onSimulate = { viewModel.simulateWeather(it) }
    )

    Spacer(modifier = Modifier.height(18.dp))

    // Smart Weather AI Insights Block
    AiInsightsCard(
        insights = state.aiInsights,
        isLoading = state.isAiLoading
    )

    Spacer(modifier = Modifier.height(18.dp))

    // Interactive Grid of 6 meteorological details
    MeteorologicalDetailsGrid(data = data)

    Spacer(modifier = Modifier.height(18.dp))

    // Horizontally Scrollable Timeline Forecast
    HourlyTimetable(
        hourlyList = data.hourlyForecasts,
        convertToDegrees = { it.tempString() }
    )

    Spacer(modifier = Modifier.height(18.dp))

    // 7-Day Almanac List
    WeeklyAlmanacSection(
        dailyList = data.dailyForecasts,
        convertToDegrees = { it.tempString() }
    )
}

@Composable
fun AtmosphereSandboxPanel(
    onSimulate: (WeatherCondition) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("simulation_panel"),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Simulator Icon",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Atmospheric Testing Sandbox",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Force unique climate parameters to test app styling & AI advice:",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            // Dynamic grid layout for simulations
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SimulationButton(
                        label = "Sunny Heat", 
                        color = Color(0xFFD97706),
                        icon = Icons.Default.PlayArrow,
                        onClick = { onSimulate(WeatherCondition.SUNNY) },
                        modifier = Modifier.weight(1f)
                    )
                    SimulationButton(
                        label = "Scatter Cloud", 
                        color = Color(0xFF4B5563),
                        icon = Icons.Default.PlayArrow,
                        onClick = { onSimulate(WeatherCondition.PARTLY_CLOUDY) },
                        modifier = Modifier.weight(1f)
                    )
                    SimulationButton(
                        label = "Heavy Drizzle", 
                        color = Color(0xFF0D9488),
                        icon = Icons.Default.PlayArrow,
                        onClick = { onSimulate(WeatherCondition.RAINY) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SimulationButton(
                        label = "Thunderstorm", 
                        color = Color(0xFF7C3AED),
                        icon = Icons.Default.PlayArrow,
                        onClick = { onSimulate(WeatherCondition.STORMY) },
                        modifier = Modifier.weight(1f)
                    )
                    SimulationButton(
                        label = "Blizzard", 
                        color = Color(0xFF2563EB),
                        icon = Icons.Default.PlayArrow,
                        onClick = { onSimulate(WeatherCondition.SNOWY) },
                        modifier = Modifier.weight(1f)
                    )
                    SimulationButton(
                        label = "Gale Winds", 
                        color = Color(0xFF059669),
                        icon = Icons.Default.PlayArrow,
                        onClick = { onSimulate(WeatherCondition.WINDY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun SimulationButton(
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier
            .height(38.dp)
            .testTag("simulate_button_${label.lowercase().replace(" ", "_")}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label, 
                color = Color.White, 
                fontSize = 10.sp, 
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AiInsightsCard(
    insights: String,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_insights_card"),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.10f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "GEMINI AI",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stylist & Activity Insights",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                        color = Color(0xFFA78BFA),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Analyzing dynamic localized patterns...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else {
                Text(
                    text = insights,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("ai_insights_paragraph")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 Advice updates instantly during simulation tests.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun MeteorologicalDetailsGrid(data: WeatherData) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Wind speed card
            DetailCard(
                title = "Wind Current",
                value = "${data.windSpeedKmh} km/h",
                subTitle = if (data.windSpeedKmh > 30) "High breeze" else "Gentle flow",
                icon = Icons.Default.Refresh,
                tint = Color(0xFF93C5FD),
                modifier = Modifier.weight(1f)
            ) {
                // Interactive wind indicator drawing
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val points = listOf(
                        Offset(10f, height * 0.4f),
                        Offset(width * 0.4f, height * 0.4f),
                        Offset(width * 0.5f, height * 0.2f),
                        Offset(width * 0.7f, height * 0.6f),
                        Offset(width - 10f, height * 0.6f)
                    )
                    drawPoints(
                        points = points,
                        pointMode = androidx.compose.ui.graphics.PointMode.Polygon,
                        color = Color.White.copy(alpha = 0.6f),
                        strokeWidth = 3f
                    )
                }
            }

            // Atmospheric Humidity card
            DetailCard(
                title = "Relative Humidity",
                value = "${data.humidityPercent}%",
                subTitle = when {
                    data.humidityPercent > 75 -> "Extremely damp"
                    data.humidityPercent < 40 -> "Arid dry"
                    else -> "Optimal comfort"
                },
                icon = Icons.Default.Info,
                tint = Color(0xFF67E8F9),
                modifier = Modifier.weight(1f)
            ) {
                // Customized linear gauge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(data.humidityPercent / 100f)
                            .background(Color(0xFF06B6D4))
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // UV index card
            DetailCard(
                title = "UV Radiation",
                value = "INDEX ${data.uvIndex}",
                subTitle = when {
                    data.uvIndex >= 8 -> "Extreme safety alert"
                    data.uvIndex >= 6 -> "Very high risk"
                    data.uvIndex >= 3 -> "Moderate radiation"
                    else -> "Safe solar levels"
                },
                icon = Icons.Default.Star,
                tint = Color(0xFFFDE047),
                modifier = Modifier.weight(1f)
            ) {
                // UV color range line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val colors = listOf(Color.Green, Color.Yellow, Color.Red, Color.Magenta)
                    colors.forEachIndexed { idx, col ->
                        val isCurrent = idx == Math.min(3, data.uvIndex / 3)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (isCurrent) col else col.copy(alpha = 0.25f))
                        )
                    }
                }
            }

            // Air Quality card
            DetailCard(
                title = "Air Quality (AQI)",
                value = "${data.aqi}",
                subTitle = when {
                    data.aqi > 150 -> "Hazardous particulate"
                    data.aqi > 100 -> "Sensitive distress"
                    data.aqi > 50 -> "Moderate quality"
                    else -> "Pristine clear air"
                },
                icon = Icons.Default.Info,
                tint = Color(0xFF34D399),
                modifier = Modifier.weight(1f)
            ) {
                // Colored slider gauge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .drawBehind {
                            drawCircle(
                                color = when {
                                    data.aqi > 150 -> Color.Red
                                    data.aqi > 100 -> Color(0xFFF59E0B)
                                    data.aqi > 50 -> Color.Yellow
                                    else -> Color.Green
                                },
                                radius = 10f,
                                center = Offset(
                                    x = (Math.min(200, data.aqi) / 200f) * size.width,
                                    y = size.height / 2
                                )
                            )
                        }
                        .background(
                            Brush.linearGradient(
                                listOf(Color.Green, Color.Yellow, Color(0xFFF59E0B), Color.Red)
                            ),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
fun DetailCard(
    title: String,
    value: String,
    subTitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier.testTag("meteorological_detail_${title.lowercase().replace(" ", "_")}"),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = value,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = subTitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun HourlyTimetable(
    hourlyList: List<HourlyForecast>,
    convertToDegrees: (Int) -> String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hourly_timetable"),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Hours Icon",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Hourly Forecast",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(hourlyList) { idx, hour ->
                    val isHighlighted = idx == 1 // Highlights the second item premium style matching 1PM in HTML
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isHighlighted) Color.White.copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isHighlighted) Color.White.copy(alpha = 0.30f) else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = hour.time,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        CustomWeatherGraphics(
                            condition = hour.condition,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = convertToDegrees(hour.temp),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${hour.popPercent}% rain",
                            color = Color(0xFF60A5FA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyAlmanacSection(
    dailyList: List<DailyForecast>,
    convertToDegrees: (Int) -> String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekly_almanac_card"),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "${dailyList.size}-Day Weather Forecast",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap on individual days to review active climate predictions:",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dailyList.forEach { dayForecast ->
                    var isExpanded by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = if (isExpanded) 0.15f else 0.05f))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = if (isExpanded) 0.25f else 0.10f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { isExpanded = !isExpanded }
                            .animateContentSize(animationSpec = spring())
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("daily_card_${dayForecast.day.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dayForecast.day,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(80.dp)
                            )

                            CustomWeatherGraphics(
                                condition = dayForecast.condition,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = dayForecast.condition.displayName,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = convertToDegrees(dayForecast.maxTemp),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Subtle bar indicator
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = convertToDegrees(dayForecast.minTemp),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Outlook: ${dayForecast.description}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Precipitation Risk: High",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Visibility Limits: Unrestricted",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated High-Fidelity Custom Canvas Composable for Dynamic Weather Artworks
 */
@Composable
fun CustomWeatherGraphics(
    condition: WeatherCondition,
    modifier: Modifier = Modifier
) {
    // Basic infinite animations for weather particles or sun spin
    val infiniteTransition = rememberInfiniteTransition(label = "Atmosphere motion")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue =  0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation =  tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Sun spin"
    )

    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Cloud bounce"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height / 2f)

        when (condition) {
            WeatherCondition.SUNNY -> {
                // Shiny Sun glowing
                drawCircle(
                    color = Color(0xFFFBBF24),
                    radius = width * 0.28f,
                    center = center
                )
                // Radiant Sun rays
                for (i in 0 until 8) {
                    val angleDeg = (i * 45) + rotationAngle
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val rayStart = width * 0.33f
                    val rayEnd = width * 0.44f
                    val startX = center.x + (Math.cos(angleRad) * rayStart).toFloat()
                    val startY = center.y + (Math.sin(angleRad) * rayStart).toFloat()
                    val endX = center.x + (Math.cos(angleRad) * rayEnd).toFloat()
                    val endY = center.y + (Math.sin(angleRad) * rayEnd).toFloat()

                    drawLine(
                        color = Color(0xFFF59E0B),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = width * 0.04f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            WeatherCondition.PARTLY_CLOUDY -> {
                // Rear Sun
                drawCircle(
                    color = Color(0xFFFBBF24),
                    radius = width * 0.22f,
                    center = Offset(center.x + width * 0.12f, center.y - height * 0.12f)
                )

                // Foreground Cloud (slight bounce)
                val hoverY = center.y + bounceOffset
                val cloudPath = Path().apply {
                    val scale = width / 100f
                    moveTo(30f * scale, hoverY + 15f * scale)
                    lineTo(70f * scale, hoverY + 15f * scale)
                    cubicTo(78f * scale, hoverY + 15f * scale, 83f * scale, hoverY + 9f * scale, 83f * scale, hoverY + 3f * scale)
                    cubicTo(83f * scale, hoverY - 4f * scale, 77f * scale, hoverY - 9f * scale, 70f * scale, hoverY - 9f * scale)
                    cubicTo(68f * scale, hoverY - 9f * scale, 65f * scale, hoverY - 8f * scale, 64f * scale, hoverY - 8f * scale)
                    cubicTo(60f * scale, hoverY - 17f * scale, 51f * scale, hoverY - 22f * scale, 45f * scale, hoverY - 22f * scale)
                    cubicTo(37f * scale, hoverY - 22f * scale, 31f * scale, hoverY - 15f * scale, 30f * scale, hoverY - 10f * scale)
                    cubicTo(29f * scale, hoverY - 10f * scale, 28f * scale, hoverY - 10f * scale, 27f * scale, hoverY - 10f * scale)
                    cubicTo(20f * scale, hoverY - 10f * scale, 17f * scale, hoverY - 4f * scale, 17f * scale, hoverY + 2f * scale)
                    cubicTo(17f * scale, hoverY + 8f * scale, 22f * scale, hoverY + 15f * scale, 30f * scale, hoverY + 15f * scale)
                }
                drawPath(path = cloudPath, color = Color.White.copy(alpha = 0.95f))
            }
            WeatherCondition.CLOUDY -> {
                // Stack of 2 grey layered clouds
                val hover1 = bounceOffset
                val hover2 = -bounceOffset
                
                // Rear Cloud (Slate dark)
                val c1Path = Path().apply {
                    val scale = width / 100f
                    val base = center.y + hover1 - 6f * scale
                    moveTo(35f * scale, base + 15f * scale)
                    lineTo(75f * scale, base + 15f * scale)
                    cubicTo(82f * scale, base + 15f * scale, 87f * scale, base + 8f * scale, 87f * scale, base + 2f * scale)
                    cubicTo(87f * scale, base - 5f * scale, 80f * scale, base - 10f * scale, 75f * scale, base - 10f * scale)
                    cubicTo(72f * scale, base - 19f * scale, 62f * scale, base - 23f * scale, 55f * scale, base - 23f * scale)
                    cubicTo(48f * scale, base - 23f * scale, 42f * scale, base - 15f * scale, 40f * scale, base - 10f * scale)
                    cubicTo(32f * scale, base - 10f * scale, 29f * scale, base - 3f * scale, 29f * scale, base + 5f * scale)
                }
                drawPath(path = c1Path, color = Color(0xFF64748B))

                // Front Cloud (Light Grey)
                val c2Path = Path().apply {
                    val scale = width / 100f
                    val base = center.y + hover2 + 8f * scale
                    moveTo(25f * scale, base + 12f * scale)
                    lineTo(65f * scale, base + 12f * scale)
                    cubicTo(71f * scale, base + 12f * scale, 76f * scale, base + 6f * scale, 76f * scale, base)
                    cubicTo(76f * scale, base - 6f * scale, 71f * scale, base - 10f * scale, 65f * scale, base - 10f * scale)
                    cubicTo(62f * scale, base - 18f * scale, 53f * scale, base - 21f * scale, 46f * scale, base - 21f * scale)
                    cubicTo(40f * scale, base - 21f * scale, 34f * scale, base - 13f * scale, 32f * scale, base - 8f * scale)
                    cubicTo(25f * scale, base - 8f * scale, 21f * scale, base - 2f * scale, 21f * scale, base + 5f * scale)
                }
                drawPath(path = c2Path, color = Color(0xFFBAC5D6))
            }
            WeatherCondition.RAINY -> {
                // Rainy Cloud (Dull blue gray)
                val hover1 = bounceOffset
                val scale = width / 100f
                val base = center.y + hover1 - 10f * scale
                
                val cloudPath = Path().apply {
                    moveTo(25f * scale, base + 15f * scale)
                    lineTo(75f * scale, base + 15f * scale)
                    cubicTo(82f * scale, base + 15f * scale, 87f * scale, base + 8f * scale, 87f * scale, base + 2f * scale)
                    cubicTo(87f * scale, base - 5f * scale, 80f * scale, base - 10f * scale, 75f * scale, base - 10f * scale)
                    cubicTo(72f * scale, base - 19f * scale, 62f * scale, base - 23f * scale, 55f * scale, base - 23f * scale)
                    cubicTo(48f * scale, base - 23f * scale, 42f * scale, base - 15f * scale, 40f * scale, base - 10f * scale)
                    cubicTo(32f * scale, base - 10f * scale, 29f * scale, base - 3f * scale, 29f * scale, base + 5f * scale)
                }
                drawPath(path = cloudPath, color = Color(0xFF475569))

                // Rain droplets falling down diagonally
                val progress = (rotationAngle % 40) / 40f
                for (i in 0 until 4) {
                    val xCoord = (30f + i * 15f) * scale
                    val yStart = base + (22f + progress * 15f) * scale
                    val yEnd = yStart + 8f * scale
                    
                    drawLine(
                        color = Color(0xFF60A5FA),
                        start = Offset(xCoord - 4f, yStart),
                        end = Offset(xCoord - 8f, yEnd),
                        strokeWidth = width * 0.02f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            WeatherCondition.STORMY -> {
                // Storm cloud (deep charcoal) and lightning bolt
                val hover1 = bounceOffset
                val scale = width / 100f
                val base = center.y + hover1 - 10f * scale
                
                val cloudPath = Path().apply {
                    moveTo(25f * scale, base + 15f * scale)
                    lineTo(75f * scale, base + 15f * scale)
                    cubicTo(82f * scale, base + 15f * scale, 87f * scale, base + 8f * scale, 87f * scale, base + 2f * scale)
                    cubicTo(87f * scale, base - 5f * scale, 80f * scale, base - 10f * scale, 75f * scale, base - 10f * scale)
                }
                drawPath(path = cloudPath, color = Color(0xFF1E293B))

                // Glowing Neon Lightning Bolt
                val isLightningActive = rotationAngle % 60 > 45
                if (isLightningActive) {
                    val lightningPath = Path().apply {
                        moveTo(50f * scale, base + 12f * scale)
                        lineTo(42f * scale, base + 24f * scale)
                        lineTo(50f * scale, base + 24f * scale)
                        lineTo(46f * scale, base + 38f * scale)
                    }
                    drawPath(
                        path = lightningPath,
                        color = Color(0xFFFDE047),
                        style = Stroke(width = 4f * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }
            WeatherCondition.SNOWY -> {
                // Snowy Cloud (pale blue/teal background)
                val hover1 = bounceOffset
                val scale = width / 100f
                val base = center.y + hover1 - 10f * scale
                
                val cloudPath = Path().apply {
                    moveTo(25f * scale, base + 15f * scale)
                    lineTo(75f * scale, base + 15f * scale)
                    cubicTo(82f * scale, base + 15f * scale, 87f * scale, base + 8f * scale, 87f * scale, base + 2f * scale)
                }
                drawPath(path = cloudPath, color = Color(0xFF6B7280))

                // Falling snowflakes (* shapes on canvas)
                val progress = (rotationAngle % 40) / 40f
                val flakeRadius = 4f * scale
                for (i in 0 until 3) {
                    val xCoord = (32f + i * 18f) * scale
                    val yCoord = base + (22f + progress * 16f) * scale
                    
                    // Snowflake ticks
                    drawLine(
                        color = Color.White,
                        start = Offset(xCoord - flakeRadius, yCoord),
                        end = Offset(xCoord + flakeRadius, yCoord),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(xCoord, yCoord - flakeRadius),
                        end = Offset(xCoord, yCoord + flakeRadius),
                        strokeWidth = 2f
                    )
                }
            }
            WeatherCondition.WINDY -> {
                // Custom wind breezes (horizontal swirling paths)
                val scale = width / 100f
                val progress = (rotationAngle % 60) / 60f
                
                for (i in 0 until 3) {
                    val xStart = -20f * scale + progress * (width * 1.4f)
                    val yCoord = (30f + i * 20f) * scale
                    
                    val windPath = Path().apply {
                        moveTo(xStart, yCoord)
                        cubicTo(xStart + 30f * scale, yCoord - 10f * scale, xStart + 50f * scale, yCoord + 10f * scale, xStart + 80f * scale, yCoord)
                    }
                    drawPath(
                        path = windPath,
                        color = Color.White.copy(alpha = 0.5f),
                        style = Stroke(width = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun ErrorStateCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.Red.copy(alpha = 0.8f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
            ) {
                Text("Retry Default", color = Color.White)
            }
        }
    }
}

@Composable
fun FrostedBottomNavigation(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("frosted_bottom_navigation"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple("Home", Icons.Default.Home, currentTab == "Home"),
                Triple("Radar", Icons.Default.Refresh, currentTab == "Radar"),
                Triple("Almanac", Icons.Default.DateRange, currentTab == "Almanac"),
                Triple("Settings", Icons.Default.Settings, currentTab == "Settings")
            )

            tabs.forEach { (label, icon, isActive) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTabSelected(label) }
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun RadarTabScreen() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("radar_panel"),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Atmospheric Micro-Radar",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Real-time localized Doppler returns & high-gale vectors:",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 18.dp)
            )

            // Canvas Radar visualizer
            val infiniteTransition = rememberInfiniteTransition()
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(2.dp, Color(0xFF10B981).copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2

                    // Concentric helper circular rings
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        radius = radius * 0.75f,
                        style = Stroke(width = 1.3f)
                    )
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        radius = radius * 0.5f,
                        style = Stroke(width = 1.3f)
                    )
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        radius = radius * 0.25f,
                        style = Stroke(width = 1.3f)
                    )

                    // Coordinate axis cross-grid
                    drawLine(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2)
                    )
                    drawLine(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height)
                    )

                    // Sweeping radar wedge sector
                    val sweepAngle = 45f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color(0xFF10B981).copy(alpha = 0.4f), Color.Transparent),
                            center = center
                        ),
                        startAngle = angle - sweepAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true
                    )

                    // Simulated static severe hazard precipitation reflection blobs
                    drawCircle(
                        color = Color(0xFFEF4444).copy(alpha = 0.6f),
                        radius = 16f,
                        center = Offset(center.x + radius * 0.45f, center.y - radius * 0.35f)
                    )
                    drawCircle(
                        color = Color(0xFFFBBF24).copy(alpha = 0.5f),
                        radius = 24f,
                        center = Offset(center.x - radius * 0.35f, center.y + radius * 0.2f)
                    )
                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = 0.4f),
                        radius = 32f,
                        center = Offset(center.x + radius * 0.2f, center.y + radius * 0.5f)
                    )
                }

                Text(
                    text = "SWEEP ACTIVE",
                    color = Color(0xFF10B981),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Info rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                RadarMetric(label = "Heavy Cells", color = Color(0xFFEF4444), "Rain/Storms")
                RadarMetric(label = "Moderate", color = Color(0xFFFBBF24), "Scattered Rain")
                RadarMetric(label = "Light Returns", color = Color(0xFF3B82F6), "Light Drizzle")
            }
        }
    }
}

@Composable
fun RadarMetric(label: String, color: Color, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        }
    }
}

@Composable
fun SettingsTabScreen(
    viewModel: WeatherViewModel,
    alertsOptedIn: Boolean,
    savedLocations: Set<String>
) {
    var newCityInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Launcher for Notification Permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleAlertsOptIn()
        } else {
            viewModel.showLocalAlert("Permission Denied", "System alerts permission denied. Notification banners won't slide down.")
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Card 1: Opt-in Severe Alerts Toggle
        Card(
            modifier = Modifier.fillMaxWidth().testTag("notifications_opt_in_card"),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Real-Time Severe Alerts",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Opt-in to real-time notification alerts for storms, blizzards, high-gales, and extreme heatwaves.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Switch(
                    checked = alertsOptedIn,
                    onCheckedChange = {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.toggleAlertsOptIn()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF10B981),
                        checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.testTag("alerts_opt_in_toggle")
                )
            }
        }

        // Card 2: Monitored Saved Locations
        Card(
            modifier = Modifier.fillMaxWidth().testTag("monitored_locations_card"),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp)
            ) {
                Text(
                    text = "Monitored Saved Locations",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Add custom locations to monitor for dynamic severe warning signals:",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                // Input Field to Save City
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCityInput,
                        onValueChange = { newCityInput = it },
                        placeholder = { Text("Add city (e.g. Chicago)...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).height(50.dp).testTag("save_city_input"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color.White.copy(alpha = 0.10f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (newCityInput.isNotBlank()) {
                                viewModel.addSavedLocation(newCityInput)
                                newCityInput = ""
                                focusManager.clearFocus()
                            }
                        })
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newCityInput.isNotBlank()) {
                                viewModel.addSavedLocation(newCityInput)
                                newCityInput = ""
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFA78BFA).copy(alpha = 0.25f))
                            .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.5f), CircleShape)
                            .testTag("add_monitored_city_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add monitored location", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List of Saved Cities
                if (savedLocations.isEmpty()) {
                    Text(
                        text = "No custom locations are currently saved.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedLocations.forEach { city ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.searchCity(city)
                                        viewModel.switchTab("Home")
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Monitored City",
                                        tint = Color(0xFFA78BFA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = city,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeSavedLocation(city) },
                                    modifier = Modifier.size(24.dp).testTag("delete_monitored_city_${city.lowercase()}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Stop monitoring $city",
                                        tint = Color(0xFFFCA5A5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 3: Alert System Diagnostic Tool
        Card(
            modifier = Modifier.fillMaxWidth().testTag("system_diagnostic_card"),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "System Alerts Hardware Verification",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Instantly trigger a simulated severe storm notification check to verify push-action deliverability on device:",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.triggerDemoAlert()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA78BFA)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("trigger_test_alert_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning Diagnostic Icon",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Trigger System Test Alert Now",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

