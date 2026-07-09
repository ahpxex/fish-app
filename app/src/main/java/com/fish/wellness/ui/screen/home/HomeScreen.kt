package com.fish.wellness.ui.screen.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fish.wellness.data.entity.BlockedAppEntity
import com.fish.wellness.data.entity.QuickBlockSessionEntity
import com.fish.wellness.data.entity.ScheduleEntity
import com.fish.wellness.util.PermissionChecker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPickApps: () -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showQuickBlockDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("FishWellness", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddSchedule,
                icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                text = { Text("New Schedule") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PermissionSection(
                    state = state,
                    onOverlaySettings = { overlayLauncher.launch(PermissionChecker.overlaySettingsIntent()) },
                    onAccessibilitySettings = { overlayLauncher.launch(PermissionChecker.accessibilitySettingsIntent()) },
                    onUsageStatsSettings = { overlayLauncher.launch(PermissionChecker.usageStatsSettingsIntent()) }
                )
            }

            item {
                QuickBlockSection(
                    activeSessions = state.activeQuickBlocks,
                    onQuickBlock = { showQuickBlockDialog = true },
                    onCancel = { viewModel.cancelQuickBlock() }
                )
            }

            item {
                BlockedAppsHeader(
                    count = state.blockedApps.size,
                    onAdd = onPickApps
                )
            }

            if (state.blockedApps.isEmpty()) {
                item { EmptyCard(text = "No apps selected. Tap \"Add Apps\" to choose which apps to block.") }
            } else {
                items(state.blockedApps, key = { it.packageName }) { app ->
                    BlockedAppItem(app = app, onRemove = { viewModel.removeBlockedApp(app.packageName) })
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Schedules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onAddSchedule) { Text("+ Add") }
                }
            }

            if (state.schedules.isEmpty()) {
                item { EmptyCard(text = "No schedules yet. Create one to block apps at specific times.") }
            } else {
                items(state.schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onToggle = { viewModel.toggleSchedule(schedule) },
                        onEdit = { onEditSchedule(schedule.id) },
                        onDelete = { viewModel.deleteSchedule(schedule.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showQuickBlockDialog) {
        QuickBlockDialog(
            onDismiss = { showQuickBlockDialog = false },
            onConfirm = { minutes ->
                viewModel.startQuickBlock(minutes)
                showQuickBlockDialog = false
            }
        )
    }
}

@Composable
private fun PermissionSection(
    state: HomeUiState,
    onOverlaySettings: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onUsageStatsSettings: () -> Unit
) {
    val allGranted = state.hasOverlayPermission && state.hasAccessibilityPermission && state.hasUsageStatsPermission

    if (!allGranted) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Permissions Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(16.dp))

                if (!state.hasOverlayPermission) {
                    PermissionItem(text = "Display over other apps", onGrant = onOverlaySettings)
                    Spacer(Modifier.height(8.dp))
                }
                if (!state.hasAccessibilityPermission) {
                    PermissionItem(text = "Accessibility service", onGrant = onAccessibilitySettings)
                    Spacer(Modifier.height(8.dp))
                }
                if (!state.hasUsageStatsPermission) {
                    PermissionItem(text = "Usage access", onGrant = onUsageStatsSettings)
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "All permissions granted — blocking is active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(text: String, onGrant: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = onGrant) { Text("Grant") }
    }
}

@Composable
private fun QuickBlockSection(
    activeSessions: List<QuickBlockSessionEntity>,
    onQuickBlock: () -> Unit,
    onCancel: () -> Unit
) {
    val active = activeSessions.firstOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (active != null) Icons.Default.Lock else Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = if (active != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active != null) "Quick Block Active" else "Quick Block",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (active != null) {
                val remaining = ((active.endAt - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                Spacer(Modifier.height(8.dp))
                Text(
                    "$remaining minutes remaining",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop Early")
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Block all selected apps immediately for a set duration.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onQuickBlock, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Blocking")
                }
            }
        }
    }
}

@Composable
private fun BlockedAppsHeader(count: Int, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Blocked Apps ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        TextButton(onClick = onAdd) { Text("+ Add Apps") }
    }
}

@Composable
private fun BlockedAppItem(app: BlockedAppEntity, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: ScheduleEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startStr = remember(schedule.startMinutes) {
        "%02d:%02d".format(schedule.startMinutes / 60, schedule.startMinutes % 60)
    }
    val endStr = remember(schedule.endMinutes) {
        "%02d:%02d".format(schedule.endMinutes / 60, schedule.endMinutes % 60)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onEdit
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(schedule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Switch(checked = schedule.enabled, onCheckedChange = { onToggle() })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("$startStr — $endStr", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatDays(schedule.daysOfWeek),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDays(daysOfWeek: Int): String {
    if (daysOfWeek == ScheduleEntity.EVERY_DAY) return "Every day"
    if (daysOfWeek == ScheduleEntity.WEEKDAYS) return "Weekdays"
    if (daysOfWeek == ScheduleEntity.WEEKENDS) return "Weekends"
    val names = mutableListOf<String>()
    val dayBits = listOf(
        "Mon" to 1, "Tue" to 2, "Wed" to 4, "Thu" to 8,
        "Fri" to 16, "Sat" to 32, "Sun" to 64
    )
    for ((name, bit) in dayBits) {
        if (daysOfWeek and bit != 0) names.add(name)
    }
    return names.joinToString(", ")
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuickBlockDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedMinutes by remember { mutableStateOf(30) }
    val options = listOf(15, 30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Block") },
        text = {
            Column {
                Text("Block all selected apps for:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedMinutes == minutes, onClick = { selectedMinutes = minutes })
                        Spacer(Modifier.width(8.dp))
                        Text(formatDuration(minutes))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selectedMinutes) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h hours"
        else -> "$m minutes"
    }
}
