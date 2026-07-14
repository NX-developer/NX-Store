package com.nxteam.nxstore.data.fdroid

import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.Source
import com.nxteam.nxstore.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object FDroidSource {
    private const val REPO = "https://f-droid.org/repo"
    private const val INDEX_URL = "$REPO/index-v2.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cache: List<AppItem>? = null

    private fun localized(element: JsonElement?): String {
        val obj = element as? JsonObject ?: return ""
        val en = obj["en-US"] ?: obj["en"] ?: obj.values.firstOrNull()
        return en?.jsonPrimitive?.contentOrNullSafe() ?: ""
    }

    private fun localizedIcon(element: JsonElement?): String {
        val obj = element as? JsonObject ?: return ""
        val entry = obj["en-US"] ?: obj["en"] ?: obj.values.firstOrNull()
        val name = (entry as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNullSafe() ?: return ""
        return REPO + name
    }

    private fun localizedScreens(element: JsonElement?): List<String> {
        val obj = element as? JsonObject ?: return emptyList()
        val phone = obj["phone"] as? JsonObject ?: return emptyList()
        val list = phone["en-US"] ?: phone["en"] ?: phone.values.firstOrNull()
        val arr = list as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNullSafe() }
            .map { REPO + it }
    }

    private fun JsonElement.contentOrNull(): String? =
        try { this.jsonPrimitive.contentOrNullSafe() } catch (e: Exception) { null }

    private suspend fun load(): List<AppItem> {
        cache?.let { return it }
        return mutex.withLock {
            cache?.let { return it }
            val built = withContext(Dispatchers.IO) {
                val raw = Http.getString(INDEX_URL)
                val root = json.parseToJsonElement(raw).jsonObject
                val packages = root["packages"]?.jsonObject ?: JsonObject(emptyMap())
                packages.mapNotNull { (pkg, value) ->
                    runCatching { parseApp(pkg, value.jsonObject) }.getOrNull()
                }
            }
            cache = built
            built
        }
    }

    private fun parseApp(pkg: String, obj: JsonObject): AppItem? {
        val meta = obj["metadata"]?.jsonObject ?: return null
        val versions = obj["versions"]?.jsonObject ?: return null

        var bestCode = -1L
        var downloadUrl: String? = null
        var versionName = ""
        var sizeBytes: Long? = null
        for ((_, v) in versions) {
            val vo = v.jsonObject
            val manifest = vo["manifest"]?.jsonObject
            val code = manifest?.get("versionCode")?.contentOrNull()?.toLongOrNull() ?: 0L
            if (code >= bestCode) {
                bestCode = code
                val file = vo["file"]?.jsonObject
                val name = file?.get("name")?.contentOrNull()
                downloadUrl = if (name != null) REPO + name else null
                versionName = manifest?.get("versionName")?.contentOrNull() ?: versionName
                sizeBytes = file?.get("size")?.contentOrNull()?.toLongOrNull()
            }
        }

        val name = localized(meta["name"]).ifBlank { pkg }
        val categories = (meta["categories"] as? JsonArray)
            ?.mapNotNull { it.contentOrNull() } ?: emptyList()

        return AppItem(
            packageName = pkg,
            name = name,
            summary = localized(meta["summary"]),
            description = localized(meta["description"]),
            iconUrl = localizedIcon(meta["icon"]),
            developer = meta["authorName"]?.contentOrNull() ?: "",
            category = categories.firstOrNull() ?: "",
            versionName = versionName,
            sizeBytes = sizeBytes,
            isPaid = false,
            source = Source.FDROID,
            downloadUrl = downloadUrl,
            storeUrl = "https://f-droid.org/packages/$pkg/",
            screenshots = localizedScreens(meta["screenshots"])
        )
    }

    suspend fun featured(limit: Int = 60): List<AppItem> =
        load().filter { it.iconUrl.isNotBlank() }.take(limit)

    suspend fun search(query: String, limit: Int = 40): List<AppItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return load().filter {
            it.name.lowercase().contains(q) ||
                it.summary.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q)
        }.take(limit)
    }

    suspend fun byPackage(pkg: String): AppItem? = load().firstOrNull { it.packageName == pkg }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else this.content
