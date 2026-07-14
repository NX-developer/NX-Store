package com.nxteam.nxstore.data.aptoide

import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.Source
import com.nxteam.nxstore.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object AptoideSource {
    private const val API = "https://ws75.aptoide.com/api/7"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun obj(element: JsonElement?): JsonObject? = element as? JsonObject
    private fun arr(element: JsonElement?): JsonArray? = element as? JsonArray

    private fun str(element: JsonElement?): String? {
        val p = element as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content
    }

    private fun long(element: JsonElement?): Long? {
        val p = element as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content.toDoubleOrNull()?.toLong()
    }

    private fun double(element: JsonElement?): Double? {
        val p = element as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content.toDoubleOrNull()
    }

    suspend fun search(query: String, limit: Int = 25): List<AppItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = "$API/apps/search/query=" + java.net.URLEncoder.encode(q, "UTF-8") + "/limit=$limit"
        val raw = runCatching { Http.getString(url) }.getOrNull() ?: return@withContext emptyList()
        val root = runCatching { json.parseToJsonElement(raw).let { obj(it) } }.getOrNull() ?: return@withContext emptyList()
        val list = arr(obj(root["datalist"])?.get("list")) ?: return@withContext emptyList()
        list.mapNotNull { runCatching { parseListItem(obj(it)!!) }.getOrNull() }
    }

    private fun parseListItem(o: JsonObject): AppItem? {
        val pkg = str(o["package"]) ?: return null
        val name = str(o["name"]) ?: pkg
        val icon = str(o["icon"]).orEmpty()
        val stats = obj(o["stats"])
        val rating = double(obj(stats?.get("rating"))?.get("avg"))?.takeIf { it in 0.1..5.0 }
        val downloads = long(stats?.get("downloads")) ?: long(stats?.get("pdownloads"))
        val size = long(o["size"])
        val file = obj(o["file"])
        val version = str(file?.get("vername")).orEmpty()
        val directPath = str(file?.get("path"))

        return AppItem(
            packageName = pkg,
            name = name,
            iconUrl = icon,
            versionName = version,
            rating = rating,
            downloads = downloads,
            downloadsLabel = formatDownloads(downloads),
            sizeBytes = size,
            isPaid = false,
            source = Source.APTOIDE,
            downloadUrl = directPath,
            storeUrl = "https://en.aptoide.com/app/$pkg"
        )
    }

    suspend fun details(pkg: String): AppItem? = withContext(Dispatchers.IO) {
        val url = "$API/app/getMeta/package_name=" + java.net.URLEncoder.encode(pkg, "UTF-8")
        val raw = runCatching { Http.getString(url) }.getOrNull() ?: return@withContext null
        val root = runCatching { json.parseToJsonElement(raw).let { obj(it) } }.getOrNull() ?: return@withContext null
        val data = obj(root["data"]) ?: return@withContext null

        val name = str(data["name"]) ?: pkg
        val icon = str(data["icon"]).orEmpty()
        val stats = obj(data["stats"])
        val rating = double(obj(stats?.get("rating"))?.get("avg"))?.takeIf { it in 0.1..5.0 }
        val downloads = long(stats?.get("downloads"))
        val developer = str(obj(data["developer"])?.get("name")).orEmpty()
        val file = obj(data["file"])
        val version = str(file?.get("vername")).orEmpty()
        val size = long(file?.get("filesize")) ?: long(data["size"])
        val directPath = str(file?.get("path")) ?: str(file?.get("path_alt"))
        val description = str(data["description"]).orEmpty()

        val media = obj(data["media"])
        val screenshots = arr(media?.get("screenshots"))
            ?.mapNotNull { str(obj(it)?.get("url")) }
            ?.take(12).orEmpty()
        val video = arr(media?.get("videos"))
            ?.firstNotNullOfOrNull { str(obj(it)?.get("url")) }

        AppItem(
            packageName = pkg,
            name = name,
            summary = description.take(140),
            description = description,
            iconUrl = icon,
            developer = developer,
            versionName = version,
            rating = rating,
            downloads = downloads,
            downloadsLabel = formatDownloads(downloads),
            sizeBytes = size,
            isPaid = false,
            source = Source.APTOIDE,
            downloadUrl = directPath,
            storeUrl = "https://en.aptoide.com/app/$pkg",
            screenshots = screenshots,
            videoUrl = video,
            enriched = true
        )
    }

    suspend fun resolveDownload(item: AppItem): AppItem {
        if (!item.downloadUrl.isNullOrBlank() && item.enriched) return item
        val enriched = details(item.packageName) ?: return item
        return item.copy(
            description = item.description.ifBlank { enriched.description },
            downloadUrl = enriched.downloadUrl ?: item.downloadUrl,
            screenshots = item.screenshots.ifEmpty { enriched.screenshots },
            videoUrl = item.videoUrl ?: enriched.videoUrl,
            versionName = item.versionName.ifBlank { enriched.versionName },
            sizeBytes = item.sizeBytes ?: enriched.sizeBytes,
            developer = item.developer.ifBlank { enriched.developer },
            enriched = true
        )
    }

    private fun formatDownloads(value: Long?): String {
        if (value == null || value <= 0) return ""
        return when {
            value >= 1_000_000_000 -> "${value / 1_000_000_000}B+"
            value >= 1_000_000 -> "${value / 1_000_000}M+"
            value >= 1_000 -> "${value / 1_000}K+"
            else -> "$value+"
        }
    }
}
