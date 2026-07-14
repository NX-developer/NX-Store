package com.nxteam.nxstore.data.apkpure

import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.Source
import com.nxteam.nxstore.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object ApkPureSource {
    private const val BASE = "https://apkpure.com"

    private val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    suspend fun search(query: String, limit: Int = 25): List<AppItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = "$BASE/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")
        val html = runCatching { Http.getString(url, headers) }.getOrNull() ?: return@withContext emptyList()
        val doc = Jsoup.parse(html, BASE)
        parseSearch(doc, limit)
    }

    private fun parseSearch(doc: Document, limit: Int): List<AppItem> {
        val out = LinkedHashMap<String, AppItem>()
        val candidates = doc.select("a[href*=/]")
        for (a in candidates) {
            if (out.size >= limit) break
            val href = a.absUrl("href")
            val pkg = extractPackage(a.attr("href")) ?: continue
            val title = a.attr("title").ifBlank {
                a.selectFirst("img")?.attr("alt") ?: a.text()
            }.trim()
            if (title.isBlank()) continue
            val icon = a.selectFirst("img")?.let { it.absUrl("data-original").ifBlank { it.absUrl("src") } } ?: ""
            if (!out.containsKey(pkg)) {
                out[pkg] = AppItem(
                    packageName = pkg,
                    name = title,
                    iconUrl = icon,
                    source = Source.APKPURE,
                    storeUrl = href,
                    isPaid = false
                )
            }
        }
        return out.values.toList()
    }

    private fun extractPackage(href: String): String? {
        val clean = href.substringBefore('?').trim('/')
        val parts = clean.split('/')
        val candidate = parts.lastOrNull() ?: return null
        return if (isPackageName(candidate)) candidate else null
    }

    private fun isPackageName(s: String): Boolean {
        if (!s.contains('.')) return false
        return s.matches(Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$"))
    }

    suspend fun resolveDownload(item: AppItem): AppItem = withContext(Dispatchers.IO) {
        val detailUrl = item.storeUrl.ifBlank { "$BASE/x/${item.packageName}" }
        val downloadPage = if (detailUrl.endsWith("/download")) detailUrl else "$detailUrl/download"
        val html = runCatching { Http.getString(downloadPage, headers) }.getOrNull()
            ?: return@withContext item
        val doc = Jsoup.parse(html, BASE)

        val link = doc.selectFirst("a#download_link")?.absUrl("href")
            ?: doc.selectFirst("a[href$=.apk]")?.absUrl("href")
            ?: doc.selectFirst("a[href*=.apk]")?.absUrl("href")
            ?: doc.selectFirst("a.download-start-btn")?.absUrl("href")

        val description = doc.selectFirst("div.description, div.translate-content")?.text() ?: item.description
        val version = doc.selectFirst("span.version, div.details-sdk span")?.text() ?: item.versionName
        val isXapk = link?.contains(".xapk", ignoreCase = true) == true

        item.copy(
            downloadUrl = if (isXapk) null else link,
            description = description,
            versionName = version
        )
    }
}
