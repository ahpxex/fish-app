package com.fish.wellness.ui.screen.apppicker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fish.wellness.model.InstalledAppInfo
import com.fish.wellness.ui.components.AppIcon

private val LIMIT_OPTIONS = listOf(0, 10, 15, 30, 45, 60, 120)

private fun limitLabel(minutes: Int): String = when (minutes) {
    0 -> "Fully blocked"
    else -> "${if (minutes >= 60) "${minutes / 60}h" else ""}${if (minutes % 60 > 0) " ${minutes % 60}m" else ""}".trim() + "/day"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    onBack: () -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Apps to Block") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onBack) { Text("Done") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Text(
                text = "${state.selectedApps.size} apps selected",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.filteredApps, key = { it.packageName }) { app ->
                        AppItemRow(
                            app = app,
                            onToggle = { viewModel.toggleBlock(app) },
                            onLimitChange = { viewModel.updateLimit(app.packageName, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItemRow(
    app: InstalledAppInfo,
    onToggle: () -> Unit,
    onLimitChange: (Int) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (app.isBlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(packageName = app.packageName, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (app.isBlocked) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Checkbox(checked = app.isBlocked, onCheckedChange = { onToggle() })
            }

            AnimatedVisibility(visible = app.isBlocked) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Timelapse,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        AssistChip(
                            onClick = { menuExpanded = true },
                            label = { Text(limitLabel(app.dailyLimitMinutes), style = MaterialTheme.typography.labelSmall) }
                        )
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            LIMIT_OPTIONS.forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text(limitLabel(mins)) },
                                    onClick = {
                                        onLimitChange(mins)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
