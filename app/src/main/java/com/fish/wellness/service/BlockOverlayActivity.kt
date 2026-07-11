package com.fish.wellness.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fish.wellness.domain.blocking.BlockReason
import com.fish.wellness.util.AppUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = AppUtils.getAppName(this, packageName)
        val reason = intent.getStringExtra(EXTRA_BLOCK_REASON)
            ?.let { runCatching { BlockReason.valueOf(it) }.getOrNull() }
            ?: BlockReason.SCHEDULE

        setContent {
            BlockOverlayScreen(
                appName = appName,
                reason = reason,
                onClose = {
                    AppUtils.goToHome(this)
                    finish()
                }
            )
        }
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_BLOCK_REASON = "extra_block_reason"

    }
}

@Composable
private fun BlockOverlayScreen(
    appName: String,
    reason: BlockReason,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.LockClock,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "This app is blocked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (reason) {
                    BlockReason.QUICK_BLOCK -> "Quick Block is active."
                    BlockReason.SCHEDULE -> "Restricted by an active policy."
                    BlockReason.DAILY_LIMIT -> "Today's app limit has been reached."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Go Home")
            }
        }
    }
}
