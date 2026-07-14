package com.nxteam.nxstore.data.play

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object PlayData {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val blobRegex = Regex(
        "AF_initDataCallback\\(\\{[^{}]*?data:(\\[.*?\\])\\s*,\\s*sideChannel",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    private val currencyRegex = Regex("^[A-Z]{3}$")
    private val priceTextRegex = Regex("^\\D{0,4}\\s?\\d[\\d.,]{0,12}\\s?\\D{0,4}$")
    private val downloadsLabelRegex = Regex("^\\d+(?:[.,]\\d+)?[KMB]?\\+?$")

    fun blobs(html: String): List<JsonElement> =
        blobRegex.findAll(html).mapNotNull { match ->
            runCatching { json.parseToJsonElement(match.groupValues[1]) }.getOrNull()
        }.toList()

    private fun walk(element: JsonElement, visit: (JsonElement) -> Unit) {
        visit(element)
        when (element) {
            is JsonArray -> element.forEach { walk(it, visit) }
            is JsonObject -> element.values.forEach { walk(it, visit) }
            else -> Unit
        }
    }

    private fun str(element: JsonElement?): String? {
        val p = element as? JsonPrimitive ?: return null
        if (p is JsonNull || !p.isString) return null
        return p.content
    }

    private fun num(element: JsonElement?): Double? {
        val p = element as? JsonPrimitive ?: return null
        if (p is JsonNull || p.isString) return null
        return p.content.toDoubleOrNull()
    }

    data class Price(val micros: Long, val formatted: String)

    fun findPrice(blobs: List<JsonElement>): Price? {
        var best: Price? = null
        for (blob in blobs) {
            walk(blob) { node ->
                if (best != null) return@walk
                val arr = node as? JsonArray ?: return@walk
                if (arr.size < 3) return@walk
                val micros = num(arr[0])?.toLong() ?: return@walk
                val currency = str(arr[1]) ?: return@walk
                val text = str(arr[2]) ?: return@walk
                if (!currencyRegex.matches(currency)) return@walk
                if (text.length > 16) return@walk
                if (!priceTextRegex.matches(text) && !text.equals("Free", true)) return@walk
                best = Price(micros, text)
            }
            if (best != null) break
        }
        return best
    }

    fun findRating(blobs: List<JsonElement>): Double? {
        for (blob in blobs) {
            var found: Double? = null
            walk(blob) { node ->
                if (found != null) return@walk
                val arr = node as? JsonArray ?: return@walk
                if (arr.size < 2) return@walk
                val value = num(arr[0]) ?: return@walk
                val label = str(arr[1]) ?: return@walk
                if (value < 1.0 || value > 5.0) return@walk
                if (!label.matches(Regex("^\\d(?:[.,]\\d)?$"))) return@walk
                found = value
            }
            if (found != null) return found
        }
        return null
    }

    fun findDownloadsLabel(blobs: List<JsonElement>): String? {
        for (blob in blobs) {
            var found: String? = null
            walk(blob) { node ->
                if (found != null) return@walk
                val arr = node as? JsonArray ?: return@walk
                if (arr.size < 3) return@walk
                val label = str(arr[0]) ?: return@walk
                val exact = num(arr[1]) ?: return@walk
                if (!downloadsLabelRegex.matches(label)) return@walk
                if (exact < 1000) return@walk
                found = label
            }
            if (found != null) return found
        }
        return null
    }

    fun parseDownloads(label: String?): Long? {
        if (label.isNullOrBlank()) return null
        val clean = label.trim().removeSuffix("+").replace(",", "")
        val multiplier = when (clean.lastOrNull()) {
            'K', 'k' -> 1_000L
            'M', 'm' -> 1_000_000L
            'B', 'b' -> 1_000_000_000L
            else -> 1L
        }
        val numberPart = if (multiplier == 1L) clean else clean.dropLast(1)
        val value = numberPart.toDoubleOrNull() ?: return null
        return (value * multiplier).toLong()
    }
}
