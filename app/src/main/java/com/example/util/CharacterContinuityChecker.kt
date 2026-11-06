package com.example.util

import com.example.data.model.CharacterInfo
import java.util.Locale

/** A conservative local consistency check; it flags contradictions but never rewrites model output. */
object CharacterContinuityChecker {
    private data class Attribute(val label: String, val contextPattern: Regex)

    private val attributes = listOf(
        Attribute("warna rambut", Regex("\\b(hair|haired)\\b", RegexOption.IGNORE_CASE)),
        Attribute("warna mata", Regex("\\b(eyes?|eye color)\\b", RegexOption.IGNORE_CASE)),
        Attribute("warna pakaian", Regex("\\b(outfit|clothing|uniform|shirt|jacket|coat|dress|robe|armor|armour)\\b", RegexOption.IGNORE_CASE))
    )
    private val colors = setOf(
        "black", "white", "red", "blue", "brown", "blond", "blonde", "green", "pink", "purple",
        "violet", "orange", "silver", "gray", "grey", "gold", "golden", "teal", "cyan", "maroon"
    )
    private val colorPattern = Regex("\\b(${colors.joinToString("|")})\\b", RegexOption.IGNORE_CASE)

    /** Compares only attributes explicitly described on both sides to avoid guessing omitted details. */
    fun findContradictions(
        previous: List<CharacterInfo>,
        current: List<CharacterInfo>
    ): List<String> {
        val currentByName = current.filter { it.name.isNotBlank() }
            .associateBy { normalizeName(it.name) }
        val warnings = mutableListOf<String>()
        for (oldCharacter in previous) {
            val newCharacter = currentByName[normalizeName(oldCharacter.name)] ?: continue
            for (attribute in attributes) {
                val oldColors = colorsNearAttribute(oldCharacter.description, attribute.contextPattern)
                val newColors = colorsNearAttribute(newCharacter.description, attribute.contextPattern)
                if (oldColors.isNotEmpty() && newColors.isNotEmpty() && oldColors.intersect(newColors).isEmpty()) {
                    warnings += "${newCharacter.name}: ${attribute.label} berbeda antar segmen (${oldColors.joinToString("/")} → ${newColors.joinToString("/")}); periksa kemungkinan perubahan desain/kostum."
                }
            }
        }
        return warnings.distinct()
    }

    private fun colorsNearAttribute(description: String, contextPattern: Regex): Set<String> {
        val text = description.lowercase(Locale.ROOT)
        val found = linkedSetOf<String>()
        for (match in contextPattern.findAll(text)) {
            val from = (match.range.first - 24).coerceAtLeast(0)
            val until = (match.range.last + 12).coerceAtMost(text.length)
            for (colorMatch in colorPattern.findAll(text.substring(from, until))) {
                val color = colorMatch.value.lowercase(Locale.ROOT)
                found += when (color) {
                    "blonde" -> "blond"
                    "grey" -> "gray"
                    else -> color
                }
            }
        }
        return found
    }

    private fun normalizeName(name: String): String = name.trim().lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}
