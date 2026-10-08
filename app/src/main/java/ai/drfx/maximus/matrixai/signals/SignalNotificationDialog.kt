package ai.drfx.maximus.matrixai.signals

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val AlertGreen = Color(0xFF00E676)
private val AlertRed = Color(0xFFFF1744)
private val AlertCyan = Color(0xFF00E5FF)
private val AlertCardBg = Color(0xFF131D2A)
private val AlertBorder = Color(0xFF1E2E42)

@Composable
fun SignalNotificationDialog(
    notificationService: SignalNotificationService,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by notificationService.settings.collectAsState()
    val recentAlerts by notificationService.recentAlerts.collectAsState()
    val totalTriggered by notificationService.totalAlertsTriggered.collectAsState()

    var notifsEnabled by remember(settings) { mutableStateOf(settings.notificationsEnabled) }
    var soundEnabled by remember(settings) { mutableStateOf(settings.soundAlertsEnabled) }
    var vibrateEnabled by remember(settings) { mutableStateOf(settings.vibrateEnabled) }
    var minConfidence by remember(settings) { mutableIntStateOf(settings.minConfidenceThreshold) }

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            Toast.makeText(context, "Push notifications allowed!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Push notification permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x3300E676)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = AlertGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Signal Push Alerts",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Local Webhook Audio & Push Service",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Permission Warning Banner if required
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x33FF9100),
                        border = BorderStroke(1.dp, Color(0xFFFF9100)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Permission Needed",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9100)
                                )
                                Text(
                                    "Android requires notification permission to show push alerts.",
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                            }
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Grant", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Confidence Threshold Control
                Text(
                    "MINIMUM CONFIDENCE THRESHOLD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AlertCyan,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Incoming webhook signals with win probability ≥ $minConfidence% will trigger push notifications and sound alerts.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // Threshold Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(70, 75, 80, 85, 90).forEach { threshold ->
                        val selected = minConfidence == threshold
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) Color(0x3300E5FF) else AlertCardBg,
                            border = BorderStroke(
                                1.dp,
                                if (selected) AlertCyan else AlertBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { minConfidence = threshold }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "≥$threshold%",
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) AlertCyan else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Toggles Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AlertCardBg,
                    border = BorderStroke(1.dp, AlertBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        // Push Notifications Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (notifsEnabled) AlertGreen else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Push Notifications", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("System status bar banner with trade targets", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = notifsEnabled,
                                onCheckedChange = { notifsEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = AlertGreen
                                )
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = AlertBorder)

                        // Sound Alerts Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = if (soundEnabled) AlertCyan else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Sound Alert Chime", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Audible notification ringtone + audio tone", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = soundEnabled,
                                onCheckedChange = { soundEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = AlertCyan
                                )
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = AlertBorder)

                        // Vibration Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Vibration,
                                    contentDescription = null,
                                    tint = if (vibrateEnabled) Color(0xFFB388FF) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Haptic Vibration", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Tactile pulse on incoming high-confidence trigger", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = vibrateEnabled,
                                onCheckedChange = { vibrateEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = Color(0xFFB388FF)
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Action Buttons for Instant Testing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val success = notificationService.triggerTestAlert(confidence = 94)
                            Toast.makeText(
                                context,
                                if (success) "⚡ High-Confidence Alert Fired! (Notification & Sound)" else "Alert simulated (below threshold or disabled)",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0x3300E676),
                            contentColor = AlertGreen
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Bolt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Test Alert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            notificationService.playAudioAlert()
                            Toast.makeText(context, "🔊 Playing audio chime...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, AlertCyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play Sound", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Recent Triggered History
                if (recentAlerts.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "RECENT ALERTS LOG ($totalTriggered Total)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Clear",
                            fontSize = 11.sp,
                            color = AlertRed,
                            modifier = Modifier.clickable { notificationService.clearAlertHistory() }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    recentAlerts.take(4).forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AlertCardBg,
                            border = BorderStroke(1.dp, AlertBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (item.direction == "BUY") "🟢" else "🔴",
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            "${item.symbol} ${item.direction} @ ${item.entryPrice}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            "${item.source} • ${item.formattedTime}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0x3300E676)
                                ) {
                                    Text(
                                        "${item.winProbability}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AlertGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    notificationService.updateSettings(
                        enabled = notifsEnabled,
                        sound = soundEnabled,
                        vibrate = vibrateEnabled,
                        minConfidence = minConfidence
                    )
                    Toast.makeText(context, "Alert settings saved!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AlertGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Settings", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White)
            }
        },
        containerColor = Color(0xFF0C131D)
    )
}
