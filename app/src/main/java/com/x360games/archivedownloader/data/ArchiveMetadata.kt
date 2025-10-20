package com.x360games.archivedownloader.data

import com.google.gson.annotations.SerializedName

data class ArchiveMetadataResponse(
    val files: List<ArchiveFile>?,
    val metadata: ArchiveItemMetadata?
)

data class ArchiveFile(
    val name: String,
    val format: String?,
    val size: String?,
    val md5: String?,
    @SerializedName("mtime")
    val modifiedTime: String?
)

data class ArchiveItemMetadata(
    val title: String?,
    val identifier: String?,
    val description: String?
)

data class ArchiveItem(
    val id: String,
    val title: String,
    val url: String,
    val files: List<ArchiveFile>
)
