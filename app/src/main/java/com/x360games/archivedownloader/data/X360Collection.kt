package com.x360games.archivedownloader.data

data class X360Collection(
    val collection: String,
    val items: List<CollectionItem>,
    val by_source: Map<String, List<String>>
)

data class CollectionItem(
    val id: String,
    val url: String,
    val source: String
)
