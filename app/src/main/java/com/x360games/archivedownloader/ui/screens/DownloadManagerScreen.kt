package com.x360games.archivedownloader.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x360games.archivedownloader.database.DownloadEntity
import com.x360games.archivedownloader.database.DownloadStatus
import com.x360games.archivedownloader.service.DownloadService
import com.x360games.archivedownloader.utils.FileUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadManagerScreen(
    downloads: List<DownloadEntity>,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    onClearFinished: () -> Unit,
    onDeleteDownloads: (List<Long>) -> Unit
) {
    val context = LocalContext.current
    var selectionMode by remember { mutableStateOf(false) }
    var selectedDownloads by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteWithFiles by remember { mutableStateOf(false) }
    
    LaunchedEffect(downloads.size) {
        selectedDownloads = selectedDownloads.filter { id -> downloads.any { it.id == id } }.toSet()
        if (selectedDownloads.isEmpty() && selectionMode) {
            selectionMode = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(if (selectionMode) "${selectedDownloads.size} selected" else "Downloads")
                        if (!selectionMode && downloads.isNotEmpty()) {
                            Text(
                                text = "${downloads.size} ${if (downloads.size == 1) "download" else "downloads"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedDownloads = emptySet()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            if (selectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                            if (selectionMode) "Cancel selection" else "Back"
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = {
                                if (selectedDownloads.size == downloads.size) {
                                    selectedDownloads = emptySet()
                                } else {
                                    selectedDownloads = downloads.map { it.id }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                if (selectedDownloads.size == downloads.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                if (selectedDownloads.size == downloads.size) "Deselect all" else "Select all"
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = selectedDownloads.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, "Delete selected")
                        }
                    } else {
                        if (downloads.isNotEmpty()) {
                            IconButton(onClick = {
                                selectionMode = true
                            }) {
                                Icon(Icons.Default.Checklist, "Select items")
                            }
                        }
                        IconButton(onClick = onClearFinished) {
                            Icon(Icons.Default.Delete, "Clear Finished")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadItemCard(
                        download = download,
                        isSelected = selectedDownloads.contains(download.id),
                        selectionMode = selectionMode,
                        onSelectionChanged = { selected ->
                            selectedDownloads = if (selected) {
                                selectedDownloads + download.id
                            } else {
                                selectedDownloads - download.id
                            }
                        },
                        onLongPress = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedDownloads = setOf(download.id)
                            }
                        },
                        onClick = { 
                            if (selectionMode) {
                                val selected = !selectedDownloads.contains(download.id)
                                selectedDownloads = if (selected) {
                                    selectedDownloads + download.id
                                } else {
                                    selectedDownloads - download.id
                                }
                            } else {
                                onNavigateToDetails(download.id)
                            }
                        },
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
                        },
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete ${selectedDownloads.size} download(s)?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Do you want to also delete the downloaded files from storage?")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteWithFiles = !deleteWithFiles }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = deleteWithFiles,
                            onCheckedChange = { deleteWithFiles = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Also delete files from storage")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDownloads.forEach { downloadId ->
                            val intent = Intent(context, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_REMOVE_DOWNLOAD
                                putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
                                putExtra(DownloadService.EXTRA_DELETE_FILE, deleteWithFiles)
                            }
                            context.startService(intent)
                        }
                        onDeleteDownloads(selectedDownloads.toList())
                        selectionMode = false
                        selectedDownloads = emptySet()
                        showDeleteDialog = false
                        deleteWithFiles = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                "No downloads yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Your downloads will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadItemCard(
    download: DownloadEntity,
    isSelected: Boolean,
    selectionMode: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "downloading")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .animateContentSize(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp,
            pressedElevation = 8.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectionChanged,
                    modifier = Modifier.align(Alignment.Top)
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = download.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = download.identifier,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    StatusChip(
                        status = download.status,
                        modifier = Modifier.then(
                            if (download.status == DownloadStatus.DOWNLOADING) 
                                Modifier.scale(scale) 
                            else Modifier
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (download.status == DownloadStatus.DOWNLOADING || 
                    download.status == DownloadStatus.PAUSED) {
                    
                    val progress = if (download.totalBytes > 0) {
                        (download.downloadedBytes.toFloat() / download.totalBytes.toFloat())
                    } else 0f
                    
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(300),
                        label = "progress"
                    )
                    
                    LinearProgressIndicator(
                        progress = animatedProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    if (download.downloadParts > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(download.downloadParts) { partIndex ->
                                val partProgress = download.getPartProgress(partIndex).coerceIn(0f, 1f)
                                val animatedPartProgress by animateFloatAsState(
                                    targetValue = if (partProgress > 0f) partProgress else (progress * (0.8f + kotlin.random.Random.nextFloat() * 0.4f)),
                                    animationSpec = tween(500),
                                    label = "partProgress$partIndex"
                                )
                                
                                LinearProgressIndicator(
                                    progress = animatedPartProgress.coerceIn(0f, 1f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "${download.downloadParts} parts downloading in parallel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${FileUtils.formatFileSize(download.downloadedBytes)} / ${FileUtils.formatFileSize(download.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (download.status == DownloadStatus.DOWNLOADING && download.speed > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${FileUtils.formatFileSize(download.speed)}/s",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (download.status == DownloadStatus.COMPLETED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = FileUtils.formatFileSize(download.totalBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                if (download.status == DownloadStatus.FAILED && download.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = download.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                if (!selectionMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ActionButtons(
                        status = download.status,
                        onPause = onPause,
                        onResume = onResume,
                        onCancel = onCancel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = this.then(
    androidx.compose.foundation.combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick
    )
)

@Composable
fun ActionButtons(
    status: DownloadStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            DownloadStatus.DOWNLOADING -> {
                FilledTonalButton(
                    onClick = onPause,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pause")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel")
                }
            }
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                FilledTonalButton(
                    onClick = onResume,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resume")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel")
                }
            }
            DownloadStatus.QUEUED -> {
                OutlinedButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel")
                }
            }
            else -> {}
        }
    }
}

@Composable
fun StatusChip(status: DownloadStatus, modifier: Modifier = Modifier) {
    val (containerColor, contentColor, text, icon) = when (status) {
        DownloadStatus.DOWNLOADING -> {
            Quadruple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                "Downloading",
                Icons.Default.Download
            )
        }
        DownloadStatus.PAUSED -> {
            Quadruple(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                "Paused",
                Icons.Default.Pause
            )
        }
        DownloadStatus.COMPLETED -> {
            Quadruple(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
                "Completed",
                Icons.Default.CheckCircle
            )
        }
        DownloadStatus.FAILED -> {
            Quadruple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                "Failed",
                Icons.Default.Error
            )
        }
        DownloadStatus.CANCELLED -> {
            Quadruple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Cancelled",
                Icons.Default.Cancel
            )
        }
        DownloadStatus.QUEUED -> {
            Quadruple(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                "Queued",
                Icons.Default.Schedule
            )
        }
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
