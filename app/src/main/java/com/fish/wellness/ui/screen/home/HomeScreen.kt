package com.fish.wellness.ui.screen.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fish.wellness.data.entity.PolicyEntity
import com.fish.wellness.data.entity.QuickBlockSessionEntity
import com.fish.wellness.util.PermissionChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewPolicy: () -> Unit,
    onEditPolicy: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showQuickBlockDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var disableTarget by remember { mutableStateOf<PolicyEntity?>(null) }
    var isPasswordSetup by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun onToggle(policy: PolicyEntity) {
        if (!policy.enabled) {
            viewModel.enablePolicy(policy)
        } else {
            scope.launch {
                isPasswordSetup = !viewModel.needsPasswordToDisable()
                passwordError = false
                disableTarget = policy
                showPasswordDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FishWellness", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.allPermissionsGranted) {
                item {
                    PermissionSection(
                        state = state,
                        onAccessibilitySettings = { settingsLauncher.launch(PermissionChecker.accessibilitySettingsIntent()) },
                        onUsageStatsSettings = { settingsLauncher.launch(PermissionChecker.usageStatsSettingsIntent()) }
                    )
                }
            }

            item {
                QuickBlockSection(
                    activeSessions = state.activeQuickBlocks,
                    nowEpochMillis = state.nowEpochMillis,
                    onQuickBlock = { showQuickBlockDialog = true },
                    onCancel = { viewModel.cancelQuickBlock() }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Policies", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onNewPolicy) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Policy")
                    }
                }
            }

            if (state.policies.isEmpty()) {
                item { EmptyCard("No policies yet. Create one to block specific apps on a schedule.") }
            } else {
                items(state.policies, key = { it.id }) { policy ->
                    PolicyCard(
                        policy = policy,
                        now = state.now,
                        onToggle = { onToggle(policy) },
                        onEdit = { onEditPolicy(policy.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showQuickBlockDialog) {
        QuickBlockDialog(
            onDismiss = { showQuickBlockDialog = false },
            onConfirm = { viewModel.startQuickBlock(it); showQuickBlockDialog = false }
        )
    }

    val target = disableTarget
    if (showPasswordDialog && target != null) {
        PasswordDialog(
            isSetupMode = isPasswordSetup,
            hasError = passwordError,
            onDismiss = { showPasswordDialog = false; disableTarget = null; passwordError = false },
            onConfirm = { password, confirm ->
                scope.launch {
                    if (isPasswordSetup) {
                        if (password.length < 4 || password != confirm) {
                            passwordError = true
                            return@launch
                        }
                        viewModel.setupPasswordAndDisable(target, password)
                        showPasswordDialog = false
                        disableTarget = null
                    } else {
                        val ok = viewModel.disableWithPassword(target, password)
                        if (ok) {
                            showPasswordDialog = false
                            disableTarget = null
                        } else {
                            passwordError = true
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun PermissionSection(
    state: HomeUiState,
    onAccessibilitySettings: () -> Unit,
    onUsageStatsSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text("Permissions Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(16.dp))
            if (!state.hasAccessibilityPermission) { PermissionItem("Accessibility service", onAccessibilitySettings); Spacer(Modifier.height(8.dp)) }
            if (state.hasDailyLimits && !state.hasUsageStatsPermission) {
                PermissionItem("Usage access for daily limits", onUsageStatsSettings)
            }
        }
    }
}

@Composable
private fun PermissionItem(text: String, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = onGrant) { Text("Grant") }
    }
}

@Composable
private fun QuickBlockSection(
    activeSessions: List<QuickBlockSessionEntity>,
    nowEpochMillis: Long,
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
                Icon(if (active != null) Icons.Default.Lock else Icons.Default.FlashOn, contentDescription = null,
                    tint = if (active != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(if (active != null) "Quick Block Active" else "Quick Block", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (active != null) {
                val remaining = ((active.endAt - nowEpochMillis + 59_999L) / 60_000L).coerceAtLeast(0)
                Spacer(Modifier.height(8.dp))
                Text("$remaining minutes remaining", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Stop Early") }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Block all apps from every policy immediately for a set duration.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onQuickBlock, modifier = Modifier.fillMaxWidth()) { Text("Start Blocking") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolicyCard(
    policy: PolicyEntity,
    now: java.time.LocalDateTime,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val isActive = policy.isCurrentlyActive(now)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive && policy.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(policy.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Switch(checked = policy.enabled, onCheckedChange = { onToggle() })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("${policy.startLabel} to ${policy.endLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(formatDays(policy.daysOfWeek), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isActive && policy.enabled) {
                Spacer(Modifier.height(4.dp))
                Text("● Active now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    isSetupMode: Boolean,
    hasError: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, confirm: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSetupMode) "Set Password" else "Enter Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (isSetupMode) "Set a password to prevent easy disabling of policies."
                    else "Enter your password to disable this policy.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = hasError
                )
                if (isSetupMode) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = hasError
                    )
                }
                if (hasError) {
                    Text(
                        if (isSetupMode) "Use at least 4 matching characters" else "Incorrect password",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password, confirm) }) {
                Text(if (isSetupMode) "Set & Disable" else "Verify & Disable")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EmptyCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(text = text, modifier = Modifier.padding(24.dp).fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuickBlockDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var selectedMinutes by remember { mutableStateOf(30) }
    val options = listOf(15, 30, 45, 60, 90, 120)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Block") },
        text = {
            Column {
                Text("Block all apps from every policy for:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                options.forEach { minutes ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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

private fun formatDays(daysOfWeek: Int): String {
    if (daysOfWeek == PolicyEntity.EVERY_DAY) return "Every day"
    if (daysOfWeek == PolicyEntity.WEEKDAYS) return "Weekdays"
    if (daysOfWeek == PolicyEntity.WEEKENDS) return "Weekends"
    val names = mutableListOf<String>()
    for ((name, bit) in listOf("Mon" to 1, "Tue" to 2, "Wed" to 4, "Thu" to 8, "Fri" to 16, "Sat" to 32, "Sun" to 64)) {
        if (daysOfWeek and bit != 0) names.add(name)
    }
    return names.joinToString(", ")
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
