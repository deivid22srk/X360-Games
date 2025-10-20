package com.x360games.archivedownloader.ui.screens

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.x360games.archivedownloader.database.DownloadEntity
import com.x360games.archivedownloader.database.DownloadStatus
import com.x360games.archivedownloader.database.SpeedHistoryEntity
import com.x360games.archivedownloader.service.DownloadService
import com.x360games.archivedownloader.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailsScreen(
    download: DownloadEntity?,
    speedHistory: List<SpeedHistoryEntity>,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (download == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Download not found")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FileInfoCard(download)
                
                ProgressCard(download)
                
                if (speedHistory.isNotEmpty()) {
                    SpeedChartCard(speedHistory)
                }
                
                StatisticsCard(download)
                
                ActionsCard(
                    download = download,
                    onPause = {
                        val intent = Intent(context, DownloadService::class.java).apply {
                            action = DownloadService.ACTION_PAUSE_DOWNLOAD
                            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, download.id)
                        }
                        context.startService(intent)
                    },
                    onResume = {
                        val intent = Intent(context, DownloadService::class.java).apply {
                            action = DownloadService.ACTION_RESUME_DOWNLOAD
                            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, download.id)
                        }
                        context.startService(intent)
                    },
                    onCancel = {
                        val intent = Intent(context, DownloadService::class.java).apply {
                            action = DownloadService.ACTION_CANCEL_DOWNLOAD
                            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, download.id)
                        }
                        context.startService(intent)
                        onNavigateBack()
                    }
                )
            }
        }
    }
}

@Composable
fun FileInfoCard(download: DownloadEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "File Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(status = download.status)
            }
            
            Divider()
            
            InfoRow("File Name", download.fileName)
            InfoRow("Identifier", download.identifier)
            InfoRow("Total Size", FileUtils.formatFileSize(download.totalBytes))
            
            if (download.status == DownloadStatus.FAILED && download.errorMessage != null) {
                Divider()
                InfoRow("Error", download.errorMessage, isError = true)
            }
        }
    }
}

@Composable
fun ProgressCard(download: DownloadEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Download Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Divider()
            
            val progress = if (download.totalBytes > 0) {
                (download.downloadedBytes.toFloat() / download.totalBytes.toFloat())
            } else 0f
            
            val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (download.status == DownloadStatus.DOWNLOADING && download.speed > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${FileUtils.formatFileSize(download.speed)}/s",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Current Speed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = FileUtils.formatFileSize(download.downloadedBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = FileUtils.formatFileSize(download.totalBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                if (download.status == DownloadStatus.DOWNLOADING && download.speed > 0) {
                    val remainingBytes = download.totalBytes - download.downloadedBytes
                    val estimatedSeconds = if (download.speed > 0) remainingBytes / download.speed else 0
                    val estimatedTime = formatDuration(estimatedSeconds)
                    
                    Text(
                        text = "Estimated time remaining: $estimatedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedChartCard(speedHistory: List<SpeedHistoryEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Speed History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Divider()
            
            if (speedHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No speed data yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                val maxSpeed = speedHistory.maxOfOrNull { it.speed } ?: 1
                val chartData = speedHistory.takeLast(50).map { it.speed / 1024f / 1024f }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    ProvideChartStyle {
                        Chart(
                            chart = lineChart(),
                            model = entryModelOf(*chartData.toTypedArray()),
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis()
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Current",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = FileUtils.formatFileSize(speedHistory.lastOrNull()?.speed ?: 0) + "/s",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Peak",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = FileUtils.formatFileSize(maxSpeed) + "/s",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsCard(download: DownloadEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Divider()
            
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            InfoRow("Started", dateFormat.format(Date(download.startTime)))
            InfoRow("Last Update", dateFormat.format(Date(download.lastUpdateTime)))
            
            val elapsedTime = (download.lastUpdateTime - download.startTime) / 1000
            InfoRow("Elapsed Time", formatDuration(elapsedTime))
            
            if (download.downloadedBytes > 0 && elapsedTime > 0) {
                val avgSpeed = download.downloadedBytes / elapsedTime
                InfoRow("Average Speed", FileUtils.formatFileSize(avgSpeed) + "/s")
            }
        }
    }
}

@Composable
fun ActionsCard(
    download: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Divider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (download.status) {
                    DownloadStatus.DOWNLOADING -> {
                        Button(
                            onClick = onPause,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pause")
                        }
                        
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel")
                        }
                    }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resume")
                        }
                        
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel")
                        }
                    }
                    DownloadStatus.QUEUED -> {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel")
                        }
                    }
                    else -> {
                        Text(
                            text = "No actions available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, isError: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "$seconds seconds"
        seconds < 3600 -> {
            val minutes = seconds / 60
            val secs = seconds % 60
            "${minutes}m ${secs}s"
        }
        else -> {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            "${hours}h ${minutes}m"
        }
    }
}

@Composable
fun StatusBadge(status: DownloadStatus) {
    val (color, text, icon) = when (status) {
        DownloadStatus.DOWNLOADING -> Triple(Color(0xFF2196F3), "Downloading", Icons.Default.Download)
        DownloadStatus.PAUSED -> Triple(Color(0xFFFF9800), "Paused", Icons.Default.Pause)
        DownloadStatus.COMPLETED -> Triple(Color(0xFF4CAF50), "Completed", Icons.Default.CheckCircle)
        DownloadStatus.FAILED -> Triple(Color(0xFFF44336), "Failed", Icons.Default.Error)
        DownloadStatus.CANCELLED -> Triple(Color(0xFF9E9E9E), "Cancelled", Icons.Default.Cancel)
        DownloadStatus.QUEUED -> Triple(Color(0xFF9C27B0), "Queued", Icons.Default.Schedule)
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
