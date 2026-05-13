package dev.usrc.alarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.usrc.alarm.ui.theme.AlarmTheme
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

enum class Screen { Main, Settings, EditVariant, Logs }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannels()

        setContent {
            AlarmTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val scheduler = remember { AlarmScheduler(context) }
    
    var settings by remember { mutableStateOf(repository.getSettings()) }
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    var editingVariant by remember { mutableStateOf<AlarmVariant?>(null) }

    fun refreshSettings() {
        settings = repository.getSettings()
    }

    BackHandler(enabled = currentScreen != Screen.Main) {
        when (currentScreen) {
            Screen.Settings -> currentScreen = Screen.Main
            Screen.EditVariant -> currentScreen = Screen.Settings
            Screen.Logs -> currentScreen = Screen.Main
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (currentScreen) {
            Screen.Main -> MainScreenOneUI(
                settings = settings,
                onOpenSettings = { currentScreen = Screen.Settings },
                onOpenLogs = { currentScreen = Screen.Logs },
                onSelectVariant = { variant ->
                    Logger.d("UI: Selecting variant ${variant.name}")
                    Thread {
                        scheduler.scheduleAlarmsForVariant(variant)
                        scheduler.cancelSecondReminder()
                        scheduler.cancelFallback()
                        refreshSettings()
                    }.start()
                },
                onScheduleReminder = {
                    scheduler.scheduleReminder(settings.reminderTime)
                    repository.updateSettings { it.copy(reminderEnabled = true) }
                    refreshSettings()
                },
                onCancelAlarms = {
                    scheduler.cancelAllAlarms()
                    scheduler.cancelReminder()
                    repository.updateSettings { it.copy(reminderEnabled = false) }
                    refreshSettings()
                }
            ) {
                scheduler.scheduleTestReminder()
            }
            Screen.Settings -> SettingsScreenOneUI(
                settings = settings,
                onBack = { currentScreen = Screen.Main },
                onSaveSettings = { updated ->
                    repository.saveSettings(updated)
                    refreshSettings()
                },
                onEditVariant = { variant ->
                    editingVariant = variant
                    currentScreen = Screen.EditVariant
                }
            ) {
                editingVariant = AlarmVariant(UUID.randomUUID().toString(), "Nowy", emptyList())
                currentScreen = Screen.EditVariant
            }
            Screen.EditVariant -> EditVariantScreenOneUI(
                variant = editingVariant!!,
                onBack = { currentScreen = Screen.Settings },
                onSave = { updated ->
                    val newVariants = settings.variants.toMutableList()
                    val index = newVariants.indexOfFirst { it.id == updated.id }
                    if (index >= 0) {
                        newVariants[index] = updated
                    } else {
                        newVariants.add(updated)
                    }
                    val newSettings = settings.copy(variants = newVariants)
                    repository.saveSettings(newSettings)
                    settings = newSettings
                    currentScreen = Screen.Settings
                }
            ) { id ->
                val newVariants = settings.variants.filter { it.id != id }
                val newSettings = settings.copy(variants = newVariants)
                repository.saveSettings(newSettings)
                settings = newSettings
                currentScreen = Screen.Settings
            }
            Screen.Logs -> LogsScreenOneUI {
                currentScreen = Screen.Main
            }
        }
    }
}

// --- One UI Components ---

@Composable
fun OneUIContainer(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Viewing Area (Top Space) - Stays on background color
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp, bottom = 32.dp, start = 24.dp, end = 24.dp)
        ) {
            onBack?.let {
                IconButton(onClick = it, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wstecz",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // Interaction Area (The Content + Bottom Bar)
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Content Layer
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .padding(top = 24.dp)
                ) {
                    content()
                }
                
                // Bottom Bar Layer (Anchored to bottom, same surface color context)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    bottomBar()
                }
            }
        }
    }
}

@Composable
fun OneUICard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.padding(vertical = 10.dp)) {
        title?.let {
            Text(
                text = it.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                fontWeight = FontWeight.SemiBold
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                content()
            }
        }
    }
}

@Composable
fun OneUIActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label, 
            style = MaterialTheme.typography.labelSmall, 
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

// --- Screens ---

@Composable
fun MainScreenOneUI(
    settings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onSelectVariant: (AlarmVariant) -> Unit,
    onScheduleReminder: () -> Unit,
    onCancelAlarms: () -> Unit,
    onTestReminder: () -> Unit
) {
    val context = LocalContext.current
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val canScheduleExactAlarms = alarmManager.canScheduleExactAlarms()

    OneUIContainer(
        title = "Alarm",
        subtitle = "Wariant na jutro",
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface, // Match interaction area
                tonalElevation = 12.dp,
                shadowElevation = 32.dp,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OneUIActionItem(
                        icon = Icons.AutoMirrored.Filled.List, 
                        label = "Logi", 
                        onClick = onOpenLogs,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = onScheduleReminder,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp),
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (settings.reminderEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                    contentDescription = "Zaplanuj cykl", 
                                    tint = Color.White, 
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    
                    OneUIActionItem(
                        icon = Icons.Default.Settings, 
                        label = "Ustawienia", 
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                OneUICard(title = "Aktualny Status") {
                    val activeVariant = settings.variants.find { it.id == settings.selectedVariantId }
                    val isTodaySelection = settings.selectedVariantDate == LocalDate.now().plusDays(1).toString()
                    val isActive = (activeVariant != null) && isTodaySelection
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isActive) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text(
                                text = if (isActive) activeVariant.name else "Brak wyboru",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                            Text(
                                text = if (isActive) "Na jutro: ${settings.selectedVariantDate}" else "Czeka na decyzję (Auto o 22:00)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        onClick = onTestReminder,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 1.dp
                    ) {
                        Text("Test 10s", modifier = Modifier.padding(14.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        onClick = onCancelAlarms,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 1.dp
                    ) {
                        Text("Anuluj", modifier = Modifier.padding(14.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Row(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    PermissionChip("Powiadomienia", hasNotificationPermission)
                    Spacer(modifier = Modifier.width(12.dp))
                    PermissionChip("System", canScheduleExactAlarms)
                }
            }

            item {
                Text(
                    "Wybierz plan na jutro",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp, start = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

                items(settings.variants) { variant ->
                    val isSelected = (settings.selectedVariantId == variant.id) &&
                        (settings.selectedVariantDate == LocalDate.now().plusDays(1).toString())
                    Surface(
                        onClick = { onSelectVariant(variant) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    tonalElevation = if (isSelected) 4.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = variant.name,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (variant.noAlarms) "Brak alarmów" else variant.alarmTimes.joinToString(", "),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(140.dp)) }
        }
    }
}

@Composable
fun PermissionChip(label: String, granted: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (granted) Color(0xFFE8F5E9).copy(alpha = 0.8f) else Color(0xFFFFEBEE).copy(alpha = 0.8f),
        contentColor = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828),
        border = androidx.compose.foundation.BorderStroke(1.dp, (if (granted) Color(0xFF2E7D32) else Color(0xFFC62828)).copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (granted) Color(0xFF2E7D32) else Color(0xFFC62828))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsScreenOneUI(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onEditVariant: (AlarmVariant) -> Unit,
    onAddVariant: () -> Unit
) {
    var reminderTime by remember { mutableStateOf(settings.reminderTime) }
    var title by remember { mutableStateOf(settings.notificationTitle) }
    var message by remember { mutableStateOf(settings.notificationMessage) }
    var dayDefaults by remember { mutableStateOf(settings.dayDefaults) }

    // Use rememberUpdatedState to ensure the latest values are captured in onDispose
    val currentReminderTime by rememberUpdatedState(reminderTime)
    val currentTitle by rememberUpdatedState(title)
    val currentMessage by rememberUpdatedState(message)
    val currentDayDefaults by rememberUpdatedState(dayDefaults)
    val currentSettings by rememberUpdatedState(settings)
    val currentOnSaveSettings by rememberUpdatedState(onSaveSettings)

    DisposableEffect(Unit) {
        onDispose {
            val updated = currentSettings.copy(
                reminderTime = currentReminderTime,
                notificationTitle = currentTitle,
                notificationMessage = currentMessage,
                dayDefaults = currentDayDefaults
            )
            currentOnSaveSettings(updated)
        }
    }

    val days = listOf("Poniedziałek" to 1, "Wtorek" to 2, "Środa" to 3, "Czwartek" to 4, "Piątek" to 5, "Sobota" to 6, "Niedziela" to 7)

    OneUIContainer(
        title = "Ustawienia",
        onBack = onBack
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                OneUICard(title = "Powiadomienia") {
                    SettingsTextField(label = "Godzina (HH:mm)", value = reminderTime) { reminderTime = it }
                    Spacer(modifier = Modifier.height(24.dp))
                    SettingsTextField(label = "Tytuł", value = title) { title = it }
                    Spacer(modifier = Modifier.height(24.dp))
                    SettingsTextField(label = "Treść", value = message) { message = it }
                }
            }

            item {
                OneUICard(title = "Harmonogram tygodniowy") {
                    days.forEach { (name, id) ->
                        var showDialog by remember { mutableStateOf(value = false) }
                        val currentVariantId = dayDefaults[id]

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDialog = true }
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = settings.variants.find { it.id == currentVariantId }?.name ?: "Nie ustawiono",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (id < 7) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        if (showDialog) {
                            AlertDialog(
                                onDismissRequest = { showDialog = false },
                                title = { Text("Wybierz dla: $name") },
                                text = {
                                    Column {
                                        settings.variants.forEach { v ->
                                            ListItem(
                                                headlineContent = { Text(v.name, fontWeight = FontWeight.Bold) },
                                                modifier = Modifier.clickable {
                                                    dayDefaults = dayDefaults.toMutableMap().apply { put(id, v.id) }
                                                    showDialog = false
                                                }
                                            )
                                        }
                                    }
                                },
                                confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Anuluj") } }
                            )
                        }
                    }
                }
            }

            item {
                OneUICard(title = "Zarządzaj wariantami") {
                    settings.variants.forEachIndexed { index, variant ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    variant.name,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    if (variant.noAlarms) "Brak alarmów" else variant.alarmTimes.joinToString(", ")
                                )
                            },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable { onEditVariant(variant) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        if (index < (settings.variants.size - 1)) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                    Button(
                        onClick = onAddVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Dodaj wariant", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(60.dp)) }
        }
    }
}

@Composable
fun SettingsTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleMedium,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVariantScreenOneUI(
    variant: AlarmVariant,
    onBack: () -> Unit,
    onSave: (AlarmVariant) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(variant.name) }
    var alarmTimes by remember { mutableStateOf(variant.alarmTimes.toList()) }
    var noAlarms by remember { mutableStateOf(variant.noAlarms) }

    OneUIContainer(
        title = name.ifEmpty { "Nowy wariant" },
        onBack = onBack
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                OneUICard(title = "Podstawowe") {
                    SettingsTextField(label = "Nazwa wariantu", value = name) { name = it }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Brak alarmów", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = noAlarms, onCheckedChange = { noAlarms = it })
                    }
                }
            }

            if (!noAlarms) {
                item {
                    OneUICard(title = "Godziny alarmów") {
                        alarmTimes.forEachIndexed { index, time ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var showTimePicker by remember { mutableStateOf(value = false) }
                                
                                Surface(
                                    modifier = Modifier.weight(1f).clickable { showTimePicker = true },
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 1.dp
                                ) {
                                    Text(
                                        text = time, 
                                        modifier = Modifier.padding(16.dp), 
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                IconButton(
                                    onClick = { 
                                        alarmTimes = alarmTimes.toMutableList().apply { removeAt(index) } 
                                    },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), CircleShape)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                                }

                                if (showTimePicker) {
                                    val state = rememberTimePickerState(
                                        initialHour = time.split(":")[0].toInt(),
                                        initialMinute = time.split(":")[1].toInt(),
                                        is24Hour = true
                                    )
                                    AlertDialog(
                                        onDismissRequest = { showTimePicker = false },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    val newTime = String.format(Locale.getDefault(), "%02d:%02d", state.hour, state.minute)
                                                    alarmTimes = alarmTimes.toMutableList().apply { set(index, newTime) }
                                                    showTimePicker = false
                                                }
                                            ) { Text("OK", fontWeight = FontWeight.Bold) }
                                        },
                                        text = { TimePicker(state = state) }
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { alarmTimes = alarmTimes.toMutableList().apply { add("08:00") } },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.AddCircleOutline, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Dodaj kolejną godzinę", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp)) {
                    Button(
                        onClick = { onSave(variant.copy(name = name, alarmTimes = alarmTimes, noAlarms = noAlarms)) },
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text("Zapisz zmiany", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }
                OutlinedButton(
                    onClick = { onDelete(variant.id) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Text("Usuń wariant", fontWeight = FontWeight.Bold)
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun LogsScreenOneUI(onBack: () -> Unit) {
    OneUIContainer(
        title = "Logi",
        subtitle = "Diagnostyka systemu",
        onBack = onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(Logger.logs.reversed()) { log ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = log,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}
