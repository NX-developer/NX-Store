package com.nxteam.nxstore.model

data class AppItem(
    val packageName: String,
    val name: String,
    val summary: String = "",
    val description: String = "",
    val iconUrl: String = "",
    val developer: String = "",
    val category: String = "",
    val versionName: String = "",
    val rating: Double? = null,
    val ratingCount: Long? = null,
    val downloads: Long? = null,
    val downloadsLabel: String = "",
    val sizeBytes: Long? = null,
    val isPaid: Boolean = false,
    val priceLabel: String = "",
    val source: Source,
    val downloadUrl: String? = null,
    val storeUrl: String = "",
    val screenshots: List<String> = emptyList(),
    val videoUrl: String? = null,
    val enriched: Boolean = false
) {
    val id: String get() = "${source.name}:$packageName"
    val installable: Boolean get() = !isPaid && !downloadUrl.isNullOrBlank()
}
