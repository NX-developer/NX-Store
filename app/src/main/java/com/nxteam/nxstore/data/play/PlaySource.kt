package com.nxteam.nxstore.data.play

import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.Source
import com.nxteam.nxstore.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object PlaySource {
    private const val BASE = "https://play.google.com"
    private const val LOCALE = "&hl=en&gl=US"

    private val ratedRegex = Regex("Rated ([0-9]+(?:\\.[0-9]+)?) star", RegexOption.IGNORE_CASE)
    private val downloadsTextRegex = Regex("^\\d+(?:[.,]\\d+)?[KMB]?\\+$")
    private val shortPriceRegex = Regex("^\\D{0,4}\\s?\\d[\\d.,]{0,12}\\s?\\D{0,4}$")
    private val youtubeRegex = Regex("https://www\\.youtube\\.com/embed/[A-Za-z0-9_-]{6,20}")
    private val playImageRegex = Regex("https://play-lh\\.googleusercontent\\.com/[A-Za-z0-9_\\-=/.]{20,}")
    private val genericAlt = setOf(
        "icon image", "thumbnail image", "screenshot image",
        "cover art", "image", "app icon", "video thumbnail"
    )
    private val ratingLikeRegex = Regex("^\\d(?:[.,]\\d)?$")

    suspend fun search(query: String, limit: Int = 25): List<AppItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = "$BASE/store/search?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&c=apps$LOCALE"
        val html = runCatching { Http.getString(url) }.getOrNull() ?: return@withContext emptyList()
        parseSearch(Jsoup.parse(html, BASE), limit)
    }

    suspend fun topCharts(limit: Int = 40): List<AppItem> = withContext(Dispatchers.IO) {
        val html = runCatching { Http.getString("$BASE/store/apps?hl=en&gl=US") }.getOrNull()
            ?: return@withContext emptyList()
        parseSearch(Jsoup.parse(html, BASE), limit)
    }

    private fun parseSearch(doc: Document, limit: Int): List<AppItem> {
        val out = LinkedHashMap<String, AppItem>()
        for (anchor in doc.select("a[href*='/store/apps/details?id=']")) {
            if (out.size >= limit) break
            val href = anchor.absUrl("href")
            val pkg = href.substringAfter("id=").substringBefore('&').substringBefore('#')
            if (pkg.isBlank() || out.containsKey(pkg)) continue

            val img = anchor.selectFirst("img")
            val title = extractTitle(anchor, img) ?: continue

            val icon = img?.let { it.absUrl("src").ifBlank { it.absUrl("data-src") } }.orEmpty()
            val scope: Element = anchor.parent() ?: anchor

            out[pkg] = AppItem(
                packageName = pkg,
                name = title,
                iconUrl = icon,
                rating = extractRating(scope.outerHtml()),
                source = Source.PLAY,
                storeUrl = "$BASE/store/apps/details?id=$pkg"
            )
        }
        return out.values.toList()
    }

    suspend fun details(pkg: String): AppItem? = withContext(Dispatchers.IO) {
        val url = "$BASE/store/apps/details?id=$pkg$LOCALE"
        val html = runCatching { Http.getString(url) }.getOrNull() ?: return@withContext null
        val doc = Jsoup.parse(html, BASE)
        val blobs = PlayData.blobs(html)

        val rawTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("h1")?.text().orEmpty()
        val name = rawTitle
            .removeSuffix(" - Apps on Google Play")
            .removeSuffix(" – Apps on Google Play")
            .trim()
            .ifBlank { pkg }

        val icon = doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
        val developer = (doc.selectFirst("a[href*='/store/apps/dev']")?.text()
            ?: doc.selectFirst("a[href*='developer?id=']")?.text()).orEmpty()

        val price = resolvePrice(doc, PlayData.findPrice(blobs))
        val downloadsLabel = extractDownloadsLabel(doc) ?: PlayData.findDownloadsLabel(blobs)
        val rating = extractRating(html) ?: PlayData.findRating(blobs)

        AppItem(
            packageName = pkg,
            name = name,
            summary = description.take(140),
            description = description,
            iconUrl = icon,
            developer = developer,
            rating = rating,
            downloads = PlayData.parseDownloads(downloadsLabel),
            downloadsLabel = downloadsLabel.orEmpty(),
            isPaid = price != null,
            priceLabel = price.orEmpty(),
            source = Source.PLAY,
            downloadUrl = null,
            storeUrl = "$BASE/store/apps/details?id=$pkg",
            screenshots = extractScreenshots(doc, icon),
            videoUrl = youtubeRegex.find(html)?.value,
            enriched = true
        )
    }

    private fun resolvePrice(doc: Document, jsonPrice: PlayData.Price?): String? {
        if (jsonPrice != null) {
            if (jsonPrice.micros <= 0L) return null
            val text = jsonPrice.formatted.trim()
            if (isValidPriceLabel(text)) return text
        }
        return domPrice(doc)
    }

    private fun domPrice(doc: Document): String? {
        val buttonTexts = doc.select("button, span, div")
            .asSequence()
            .map { it.ownText().trim() }
            .filter { it.isNotBlank() && it.length <= 16 }
            .toList()
        if (buttonTexts.any { it.equals("Install", true) }) return null
        return buttonTexts.firstOrNull { isValidPriceLabel(it) }
    }

    private fun isValidPriceLabel(text: String): Boolean {
        if (text.length !in 2..16) return false
        if (text.equals("Free", true)) return false
        if (!text.any { it.isDigit() }) return false
        if (!shortPriceRegex.matches(text)) return false
        val hasCurrencyMark = text.any { it in "$€£₺₹¥₩₪R" } || text.contains(Regex("[A-Z]{3}"))
        return hasCurrencyMark
    }

    private fun extractTitle(anchor: Element, img: Element?): String? {
        val candidates = ArrayList<String>()
        anchor.attr("aria-label").trim().let { if (it.isNotBlank()) candidates.add(it) }
        img?.attr("alt")?.trim()?.let { if (it.isNotBlank()) candidates.add(it) }
        for (el in anchor.select("span, div")) {
            val t = el.ownText().trim()
            if (t.isNotBlank()) candidates.add(t)
        }
        return candidates.firstOrNull { c ->
            c.length in 2..60 &&
                c.lowercase() !in genericAlt &&
                c.any { it.isLetter() } &&
                !ratingLikeRegex.matches(c) &&
                !isValidPriceLabel(c)
        }
    }

    private fun extractRating(html: String): Double? {
        val value = ratedRegex.find(html)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return if (value in 1.0..5.0) value else null
    }

    private fun extractDownloadsLabel(doc: Document): String? {
        val labelEl = doc.allElements.firstOrNull { it.ownText().trim() == "Downloads" } ?: return null
        val container = labelEl.parent() ?: return null
        val candidate = container.children()
            .map { it.text().trim() }
            .firstOrNull { downloadsTextRegex.matches(it) }
        return candidate
    }

    private fun extractScreenshots(doc: Document, icon: String): List<String> {
        val urls = LinkedHashSet<String>()
        for (img in doc.select("img[src*=play-lh], img[data-src*=play-lh], img[srcset*=play-lh]")) {
            val alt = img.attr("alt").trim().lowercase()
            if (alt in genericAlt && alt != "screenshot image") continue
            val src = img.absUrl("src").ifBlank { img.absUrl("data-src") }
            if (src.isBlank() || src == icon) continue
            val width = img.attr("width").toIntOrNull() ?: 0
            val height = img.attr("height").toIntOrNull() ?: 0
            val bigEnough = (width == 0 && height == 0) || width > 200 || height > 200
            val isScreenshot = alt == "screenshot image"
            if (!isScreenshot && !bigEnough) continue
            urls.add(src)
        }
        return urls.take(12).toList()
    }
}
