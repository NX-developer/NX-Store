package com.nxteam.nxstore.data.fdroid

import com.nxteam.nxstore.model.AppItem
import com.nxteam.nxstore.model.Source
import com.nxteam.nxstore.network.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FDroidSource {
    private const val SITE = "https://f-droid.org"
    private const val SEARCH = "https://search.f-droid.org"

    private val sizeRegex = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(KiB|MiB|GiB|KB|MB|GB)", RegexOption.IGNORE_CASE)

    suspend fun search(query: String, limit: Int = 30): List<AppItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = "$SEARCH/?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&lang=en"
        val html = runCatching { Http.getString(url) }.getOrNull() ?: return@withContext emptyList()
        parseList(Jsoup.parse(html, SEARCH), limit)
    }

    suspend fun featured(limit: Int = 40): List<AppItem> = withContext(Dispatchers.IO) {
        val html = runCatching { Http.getString("$SITE/en/packages/") }.getOrNull()
            ?: return@withContext emptyList()
        parseList(Jsoup.parse(html, SITE), limit)
    }

    private fun parseList(doc: Document, limit: Int): List<AppItem> {
        val out = LinkedHashMap<String, AppItem>()
        for (link in doc.select("a[href*='/packages/']")) {
            if (out.size >= limit) break
            val pkg = extractPackage(link.attr("href")) ?: continue
            if (out.containsKey(pkg)) continue

            val name = (
                link.selectFirst(".package-name, h4, h3")?.text()
                    ?: link.selectFirst("img")?.attr("alt")
                    ?: link.text()
                ).orEmpty().trim().takeIf { it.isNotBlank() && it.length <= 80 } ?: continue
            val summary = link.selectFirst(".package-summary, .package-desc")?.text()?.trim().orEmpty()
            val icon = link.selectFirst("img")?.let { absImage(it) }.orEmpty()

            out[pkg] = AppItem(
                packageName = pkg,
                name = name,
                summary = summary,
                iconUrl = icon,
                source = Source.FDROID,
                storeUrl = "$SITE/en/packages/$pkg/"
            )
        }
        return out.values.toList()
    }

    private fun absImage(img: Element): String {
        val src = img.absUrl("src").ifBlank { img.absUrl("data-src") }
        return src
    }

    private fun extractPackage(href: String): String? {
        val marker = "/packages/"
        val start = href.indexOf(marker)
        if (start < 0) return null
        val rest = href.substring(start + marker.length).trim('/')
        val candidate = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (candidate.isBlank()) return null
        if (!candidate.contains('.')) return null
        return if (candidate.matches(Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$"))) candidate else null
    }

    suspend fun byPackage(pkg: String): AppItem? = withContext(Dispatchers.IO) {
        val url = "$SITE/en/packages/$pkg/"
        val html = runCatching { Http.getString(url) }.getOrNull() ?: return@withContext null
        val doc = Jsoup.parse(html, SITE)

        val name = doc.selectFirst("h3.package-name, .package-title h3, h3")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty().ifBlank { pkg }
        val summary = doc.selectFirst(".package-summary")?.text()?.trim().orEmpty()
        val descriptionHtml = doc.selectFirst(".package-description")?.html().orEmpty()
        val icon = doc.selectFirst("img.package-icon")?.let { absImage(it) }
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()

        val versionBlock = doc.selectFirst(".package-version")
        val versionName = versionBlock?.selectFirst(".package-version-header a, .package-version-header")
            ?.text()?.trim()?.substringAfter("Version ")?.substringBefore(" ")?.trim().orEmpty()

        val downloadUrl = doc.select("a[href$=.apk]")
            .map { it.absUrl("href") }
            .firstOrNull { href ->
                val file = href.substringAfterLast('/').substringBefore('?')
                file.startsWith("${pkg}_") || file.startsWith("$pkg-") || file == "$pkg.apk"
            }
        val sizeText = versionBlock?.text().orEmpty()
        val sizeBytes = parseSize(sizeText)

        val screenshots = doc.select(".screenshot img, .package-screenshots img")
            .mapNotNull { absImage(it).takeIf { url -> url.isNotBlank() } }
            .take(12)

        val category = doc.select("a[href*='/categories/']").firstOrNull()?.text()?.trim().orEmpty()

        AppItem(
            packageName = pkg,
            name = name,
            summary = summary,
            description = descriptionHtml,
            iconUrl = icon,
            category = category,
            versionName = versionName,
            sizeBytes = sizeBytes,
            isPaid = false,
            source = Source.FDROID,
            downloadUrl = downloadUrl,
            storeUrl = url,
            screenshots = screenshots,
            enriched = true
        )
    }

    private fun parseSize(text: String): Long? {
        val match = sizeRegex.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase()) {
            "KIB", "KB" -> 1024L
            "MIB", "MB" -> 1024L * 1024
            "GIB", "GB" -> 1024L * 1024 * 1024
            else -> 1L
        }
        return (value * multiplier).toLong()
    }
}
