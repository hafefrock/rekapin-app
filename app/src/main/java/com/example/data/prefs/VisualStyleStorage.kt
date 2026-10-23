package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.VisualStyle
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class VisualStyleStorage(context: Context) {
    companion object {
        private const val PREFS_NAME = "rekapin_visual_styles_prefs"
        private const val KEY_CUSTOM_STYLES = "custom_visual_styles"
        private const val KEY_SELECTED_STYLE_ID = "selected_visual_style_id"
        private const val KEY_ANALYSIS_STYLE_PREFIX = "analysis_style_"

        val BUILT_IN_STYLES = listOf(
            VisualStyle(
                id = "builtin_anime_2d_standard",
                name = "Anime 2D standar",
                promptDescription = "2D Japanese anime style, high-definition, clean cel-shaded linework, vibrant colors, detailed background art",
                isBuiltIn = true
            ),
            VisualStyle(
                id = "builtin_shonen_action",
                name = "Shonen (aksi)",
                promptDescription = "2D Japanese shonen anime style, high-definition, bold dynamic linework, high-contrast cel shading, intense saturated colors, speed lines and impact effects, detailed backgrounds",
                isBuiltIn = true
            ),
            VisualStyle(
                id = "builtin_shoujo_romance",
                name = "Shoujo (romantis)",
                promptDescription = "2D Japanese shoujo anime style, high-definition, delicate thin linework, soft pastel colors, sparkling highlights, large expressive eyes, light-bloom accents, gentle backgrounds",
                isBuiltIn = true
            ),
            VisualStyle(
                id = "builtin_seinen_mature",
                name = "Seinen (dewasa)",
                promptDescription = "2D Japanese seinen anime style, high-definition, realistic proportions, muted color palette, fine linework, moody cinematic shading, richly detailed backgrounds",
                isBuiltIn = true
            ),
            VisualStyle(
                id = "builtin_slice_of_life",
                name = "Slice of life (lembut)",
                promptDescription = "2D Japanese slice-of-life anime style, high-definition, clean soft linework, warm natural colors, gentle lighting, painterly detailed backgrounds",
                isBuiltIn = true
            ),
            VisualStyle(
                id = "builtin_retro_90s",
                name = "Retro 90-an",
                promptDescription = "2D Japanese 1990s retro anime style, classic cel animation look, slight film grain, hand-painted backgrounds, muted vintage colors",
                isBuiltIn = true
            ),
            VisualStyle(
                id = "builtin_comedy_chibi",
                name = "Komedi / chibi",
                promptDescription = "2D Japanese comedic anime style, high-definition, exaggerated expressions, chibi-style deformation, bright flat colors, bold outlines, simple clean backgrounds",
                isBuiltIn = true
            ),
            VisualStyle(
                id = "builtin_dark_fantasy",
                name = "Fantasi gelap",
                promptDescription = "2D Japanese dark fantasy anime style, high-definition, dramatic chiaroscuro lighting, deep desaturated palette with selective accent colors, ornate detailed backgrounds, atmospheric mist and particles",
                isBuiltIn = true
            )
        )

        val DEFAULT_STYLE = BUILT_IN_STYLES[0]
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllStyles(): List<VisualStyle> {
        val custom = getCustomStyles()
        return BUILT_IN_STYLES + custom
    }

    fun getCustomStyles(): List<VisualStyle> {
        val jsonStr = prefs.getString(KEY_CUSTOM_STYLES, null) ?: return emptyList()
        val list = mutableListOf<VisualStyle>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    VisualStyle(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        promptDescription = obj.getString("promptDescription"),
                        isBuiltIn = false
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveCustomStyles(styles: List<VisualStyle>) {
        val array = JSONArray()
        styles.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("promptDescription", s.promptDescription)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_STYLES, array.toString()).apply()
    }

    fun addCustomStyle(name: String, promptDescription: String): VisualStyle {
        val trimmedDesc = promptDescription.trim().removeSuffix(".")
        val newStyle = VisualStyle(
            id = "custom_" + UUID.randomUUID().toString(),
            name = name.trim(),
            promptDescription = trimmedDesc,
            isBuiltIn = false
        )
        val current = getCustomStyles().toMutableList()
        current.add(newStyle)
        saveCustomStyles(current)
        return newStyle
    }

    fun updateCustomStyle(id: String, name: String, promptDescription: String): Boolean {
        val current = getCustomStyles().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx == -1) return false
        val trimmedDesc = promptDescription.trim().removeSuffix(".")
        current[idx] = current[idx].copy(
            name = name.trim(),
            promptDescription = trimmedDesc
        )
        saveCustomStyles(current)
        return true
    }

    fun deleteCustomStyle(id: String): Boolean {
        val current = getCustomStyles().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            saveCustomStyles(current)
            if (getSelectedStyleId() == id) {
                setSelectedStyleId(DEFAULT_STYLE.id)
            }
        }
        return removed
    }

    fun deleteStyle(id: String): Boolean = deleteCustomStyle(id)

    fun getSelectedStyleId(): String {
        return prefs.getString(KEY_SELECTED_STYLE_ID, DEFAULT_STYLE.id) ?: DEFAULT_STYLE.id
    }

    fun setSelectedStyleId(id: String) {
        prefs.edit().putString(KEY_SELECTED_STYLE_ID, id).apply()
    }

    fun setSelectedStyle(style: VisualStyle) {
        setSelectedStyleId(style.id)
    }

    fun getSelectedStyle(): VisualStyle {
        val id = getSelectedStyleId()
        return getAllStyles().find { it.id == id } ?: DEFAULT_STYLE
    }

    fun saveStyleForAnalysis(analysisId: Long, style: VisualStyle) {
        val obj = JSONObject().apply {
            put("id", style.id)
            put("name", style.name)
            put("promptDescription", style.promptDescription)
            put("isBuiltIn", style.isBuiltIn)
        }
        prefs.edit().putString(KEY_ANALYSIS_STYLE_PREFIX + analysisId, obj.toString()).apply()
    }

    fun getStyleForAnalysis(analysisId: Long): VisualStyle? {
        val raw = prefs.getString(KEY_ANALYSIS_STYLE_PREFIX + analysisId, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            VisualStyle(
                id = obj.optString("id", DEFAULT_STYLE.id),
                name = obj.optString("name", DEFAULT_STYLE.name),
                promptDescription = obj.optString("promptDescription", DEFAULT_STYLE.promptDescription),
                isBuiltIn = obj.optBoolean("isBuiltIn", false)
            )
        } catch (_: Exception) {
            null
        }
    }
}
