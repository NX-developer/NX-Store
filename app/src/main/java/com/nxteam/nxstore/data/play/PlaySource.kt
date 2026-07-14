package com.nxteam.nxstore.data.play

import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.Source
import com.nxteam.nxstore.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object PlaySource {
    private const val BASE = "https://play.google.com"
    private const val LOCALE = "&hl=en&gl=US"

    private val priceRegex = Regex("\"([^\"]*?[€\$£₺₹¥]\\s?\\d+[.,]\\d{2}[^\"]*?)\"")

    suspend fun search(query: String, limit: Int = 25): List<AppItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = "$BASE/store/search?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&c=apps$LOCALE"
        val html = runCatching { Http.getString(url) }.getOrNull() ?: return@withContext emptyList()
        val doc = Jsoup.parse(html, BASE)
        parseSearch(doc, limit)
    }

    private fun parseSearch(doc: Document, limit: Int): List<AppItem> {
        val out = LinkedHashMap<String, AppItem>()
        val anchors = doc.select("a[href*='/store/apps/details?id=']")
        for (a in anchors) {
            if (out.size >= limit) break
            val href = a.absUrl("href")
            val pkg = href.substringAfter("id=").substringBefore('&').substringBefore('#')
            if (pkg.isBlank() || out.containsKey(pkg)) continue
            val img = a.selectFirst("img")
            val title = img?.attr("alt")?.ifBlank { a.attr("aria-label") } ?: a.attr("aria-label")
            if (title.isNullOrBlank()) continue
            val icon = img?.let { it.absUrl("src").ifBlank { it.absUrl("data-src") } } ?: ""
            out[pkg] = AppItem(
                packageName = pkg,
                name = title.trim(),
                iconUrl = icon,
                source = Source.PLAY,
                storeUrl = "$BASE/store/apps/details?id=$pkg",
                isPaid = false
            )
        }
        return out.values.toList()
    }

    suspend fun details(pkg: String): AppItem? = withContext(Dispatchers.IO) {
        val url = "$BASE/store/apps/details?id=$pkg$LOCALE"
        val html = runCatching { Http.getString(url) }.getOrNull() ?: return@withContext null
        val doc = Jsoup.parse(html, BASE)

        val name = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("h1")?.text() ?: pkg
        val icon = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
        val developer = doc.selectFirst("a[href*=/store/apps/dev]")?.text()
            ?: doc.selectFirst("a[href*=developer?id=]")?.text() ?: ""

        val paidLabel = detectPrice(html)
        val isPaid = paidLabel != null

        AppItem(
            packageName = pkg,
            name = name.removeSuffix(" - Apps on Google Play").trim(),
            summary = description.take(140),
            description = description,
            iconUrl = icon,
            developer = developer,
            isPaid = isPaid,
            priceLabel = paidLabel ?: "",
            source = Source.PLAY,
            downloadUrl = null,
            storeUrl = "$BASE/store/apps/details?id=$pkg"
        )
    }

    private fun detectPrice(html: String): String? {
        if (html.contains("\"Install\"") && !html.contains("\"Buy\"")) return null
        val match = priceRegex.find(html) ?: return null
        val raw = match.groupValues[1].trim()
        if (raw.contains("0.00")) return null
        return raw
    }
}
