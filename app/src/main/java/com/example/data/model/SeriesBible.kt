package com.example.data.model

data class BibleLocation(
    val name: String,
    val description: String
)

data class BibleGlossaryItem(
    val term: String,
    val definition: String
)

data class BibleRelationship(
    val characterA: String,
    val characterB: String,
    val relationship: String
)

data class BibleEpisode(
    val videoName: String,
    val recap: String
)

data class SeriesBible(
    val locations: List<BibleLocation> = emptyList(),
    val glossary: List<BibleGlossaryItem> = emptyList(),
    val relationships: List<BibleRelationship> = emptyList(),
    val plotSoFar: String = "",
    val episodes: List<BibleEpisode> = emptyList()
) {
    companion object {
        const val MAX_LOCATIONS = 20
        const val MAX_GLOSSARY = 30
        const val MAX_RELATIONSHIPS = 20
        const val MAX_PLOT_CHARS = 1200
        const val MAX_EPISODES = 10
        const val MAX_EPISODE_RECAP_CHARS = 300
    }

    fun isEmpty(): Boolean {
        return locations.isEmpty() &&
                glossary.isEmpty() &&
                relationships.isEmpty() &&
                plotSoFar.isBlank() &&
                episodes.isEmpty()
    }

    fun withUpdatedEpisode(videoName: String, recap: String): SeriesBible {
        val normalizedName = videoName.trim().lowercase(java.util.Locale.ROOT)
        val uniqueEpisodes = linkedMapOf<String, BibleEpisode>()
        val updatedEpisode = BibleEpisode(videoName.trim(), recap.trim())
        var replacedExisting = false
        episodes.forEach { episode ->
            val key = episode.videoName.trim().lowercase(java.util.Locale.ROOT)
            if (key.isNotBlank()) {
                if (key == normalizedName) {
                    if (!replacedExisting) uniqueEpisodes[key] = updatedEpisode
                    replacedExisting = true
                } else if (!uniqueEpisodes.containsKey(key)) {
                    uniqueEpisodes[key] = episode.copy(videoName = episode.videoName.trim())
                }
            }
        }
        if (normalizedName.isNotBlank() && !replacedExisting) {
            uniqueEpisodes[normalizedName] = updatedEpisode
        }
        return copy(episodes = uniqueEpisodes.values.toList()).trimToLimits()
    }

    /**
     * Memangkas data memori seri agar tidak melebihi batasan ukuran yang ditentukan:
     * - Maksimal 20 lokasi
     * - Maksimal 30 istilah (glossary)
     * - Maksimal 20 relasi karakter
     * - plotSoFar maksimal 1200 karakter
     * - Maksimal 10 episode terakhir dengan recap masing-masing maksimal 300 karakter
     */
    fun trimToLimits(): SeriesBible {
        return copy(
            locations = locations.take(MAX_LOCATIONS).map {
                it.copy(name = it.name.trim(), description = it.description.trim())
            },
            glossary = glossary.take(MAX_GLOSSARY).map {
                it.copy(term = it.term.trim(), definition = it.definition.trim())
            },
            relationships = relationships.take(MAX_RELATIONSHIPS).map {
                it.copy(
                    characterA = it.characterA.trim(),
                    characterB = it.characterB.trim(),
                    relationship = it.relationship.trim()
                )
            },
            plotSoFar = plotSoFar.trim().take(MAX_PLOT_CHARS),
            episodes = episodes.takeLast(MAX_EPISODES).map {
                it.copy(
                    videoName = it.videoName.trim(),
                    recap = it.recap.trim().take(MAX_EPISODE_RECAP_CHARS)
                )
            }
        )
    }
}
