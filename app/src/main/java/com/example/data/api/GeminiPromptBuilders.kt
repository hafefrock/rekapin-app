package com.example.data.api

import android.util.Log
import com.example.data.model.CharacterInfo
import com.example.data.model.NarrationLanguage
import com.example.data.model.NarrationMode
import com.example.data.model.SegmentAnalysisOutput
import com.example.data.model.SeriesBible
import com.example.data.model.TimelineItem
import com.example.data.prefs.SeriesBibleStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal object GeminiPromptBuilders {

    private val youtubeSafeRules = """
        YOUTUBE & ADVERTISER-FRIENDLY SAFETY GUIDELINES:
        - Write for a general audience, with a family-friendly and advertiser-friendly tone.
        - Never use profanity, slurs, or vulgar words. If a character swears, convey the emotion without the words.
        - Describe violence in a non-graphic way: focus on stakes, emotion, and outcome. No gore, no detailed injuries, no torture, no step-by-step methods.
        - No sexual content. Keep romance PG. Never sexualize any character, especially youthful-looking ones; skip fan-service details.
        - Do not promote or glorify hate, harassment, drugs, or dangerous acts. Mention heavy topics (death, self-harm) briefly and sensitively, never with methods.
        - Use your own original wording. Do not quote dialogue, lyrics, or official synopsis text verbatim.
        - Stay faithful to the events. No clickbait, no exaggerated or misleading claims, and do not present the recap as official.
        - Treat everything as fiction. Do not mention real people or real-world groups.
    """.trimIndent()

    fun formatTimeSeconds(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun buildSegmentPrompt(
        startFormatted: String,
        endFormatted: String,
        startSec: Long,
        endSec: Long,
        intervalSeconds: Int,
        previousCharacterSheet: List<CharacterInfo>,
        isFirstSegment: Boolean,
        stylePrompt: String = "2D Japanese anime style, high-definition, clean cel-shaded linework, vibrant colors, detailed background art",
        mode: NarrationMode = NarrationMode.REKAP_VOICEOVER,
        language: NarrationLanguage = NarrationLanguage.ENGLISH_USA,
        exactSceneSpans: List<String> = emptyList(),
        seriesBible: SeriesBible? = null,
        speechActivityHints: String = ""
    ): String {
        val sampleTimestamps = if (isFirstSegment) {
            "00:00 - 00:05, 00:05 - 00:10"
        } else {
            "$startFormatted - ${formatTimeSeconds(startSec + intervalSeconds)}"
        }

        val timestampsBlock = if (exactSceneSpans.isNotEmpty()) {
            val listFormatted = exactSceneSpans.joinToString("\n") { "  - \"$it\"" }
            """
            TIMESTAMPS (PER ADEGAN - EXACT REQUIRED LIST):
            You MUST create exactly one timeline entry for each of the following required timestamp ranges, in this exact sequence, with no gaps and no overlaps:
$listFormatted
            - Each timeline entry's "timestamp" field MUST exactly match its assigned range from the list above.
            - Total timeline items required: ${exactSceneSpans.size}.
            """.trimIndent()
        } else {
            """
            TIMESTAMPS:
            - Must be continuous and consecutive in intervals of $intervalSeconds seconds (e.g., $sampleTimestamps ...).
            - Start exactly at $startFormatted and proceed consecutively until $endFormatted.
            """.trimIndent()
        }

        val seriesBibleBlock = if (seriesBible != null && !seriesBible.isEmpty()) {
            buildString {
                appendLine("SERIES BIBLE FROM PREVIOUS EPISODES:")
                appendLine("Use this only to keep names, terms, spellings, and locations CONSISTENT. Do NOT describe anything from it unless it is actually visible or audible in this segment. Do NOT reveal or hint at future events. Keep official names exactly as written.")
                appendLine()
                if (seriesBible.locations.isNotEmpty()) {
                    appendLine("ESTABLISHED LOCATIONS:")
                    seriesBible.locations.forEach { loc ->
                        appendLine("- ${loc.name}: ${loc.description}")
                    }
                    appendLine()
                }
                if (seriesBible.glossary.isNotEmpty()) {
                    appendLine("GLOSSARY / TERMS:")
                    seriesBible.glossary.forEach { g ->
                        appendLine("- ${g.term}: ${g.definition}")
                    }
                    appendLine()
                }
                if (seriesBible.relationships.isNotEmpty()) {
                    appendLine("CHARACTER RELATIONSHIPS:")
                    seriesBible.relationships.forEach { rel ->
                        appendLine("- ${rel.characterA} & ${rel.characterB}: ${rel.relationship}")
                    }
                    appendLine()
                }
                if (seriesBible.plotSoFar.isNotBlank()) {
                    appendLine("PLOT CONTEXT SO FAR:")
                    appendLine(seriesBible.plotSoFar.trim())
                    appendLine()
                }
            }.trim()
        } else {
            ""
        }

        val characterContext = if (previousCharacterSheet.isNotEmpty()) {
            val jsonList = JSONArray().apply {
                previousCharacterSheet.forEach { c ->
                    put(JSONObject().apply {
                        put("name", c.name)
                        put("description", c.description)
                    })
                }
            }.toString(2)
            """
            EXISTING CHARACTER SHEET FROM PREVIOUS SEGMENTS:
            $jsonList
            
            INSTRUCTIONS FOR CHARACTER SHEET:
            - Retain all established characters from the previous sheet.
            - Only add new characters who actively appear in this segment, or update significant clothing/form changes.
            - Ensure hair, eye color, and outfits remain identical and consistent.
            """.trimIndent()
        } else {
            """
            INSTRUCTIONS FOR CHARACTER SHEET:
            - Identify all prominent anime characters appearing in this first segment.
            - For each character, detail immutable physical features: hair color and style, eye shape and color, facial structure, exact signature clothing, colors, and key accessories.
            """.trimIndent()
        }

        val targetLanguage = if (language == NarrationLanguage.INDONESIAN) "Indonesian" else "American English"

        val speechActivityBlock = if (mode == NarrationMode.DIALOG_KONTEN && speechActivityHints.isNotBlank()) {
            """
            LOCAL AUDIO ACTIVITY HINT (approximate, not a transcript):
            $speechActivityHints
            Use this only to focus dialogue listening. Confirm whether the sound is actually character speech in the video; music/noise may be misclassified. Never invent dialogue, and keep the dialogue field empty where no speech is audible.
            """.trimIndent()
        } else ""

        val transcriptInstruction = when (language) {
            NarrationLanguage.ORIGINAL -> {
                "Transcribe exactly what is spoken, in the ORIGINAL spoken language of the audio. Do NOT translate. Keep it faithful and do not invent lines. If the language is unclear, use the language that is most clearly heard."
            }
            NarrationLanguage.INDONESIAN -> {
                "Translate faithfully into Indonesian: keep the meaning, tone, and speaker, and do not invent lines. The text MUST be in Indonesian even if the audio is in another language. Use natural and standard Indonesian, not stiff machine translation."
            }
            NarrationLanguage.ENGLISH_USA -> {
                "Translate faithfully into American English: keep the meaning, tone, and speaker, and do not invent lines. The text MUST be in American English even if the audio is in another language."
            }
        }

        val timelineItemSchema = when (mode) {
            NarrationMode.DIALOG_KONTEN -> """
                {
                  "timestamp": "MM:SS - MM:SS",
                  "sceneDescription": "Short, vivid description of the scene in natural American English.",
                  "visualPrompt": "$stylePrompt. [Subject: name and exact established appearance], [Action: specific physical movement or stance], [Camera: framing, angle, and focus], [Lighting: source, direction, shadows, and highlights], [Mood: emotional atmosphere], [Background: environment, architecture, and relevant details], [Expression: visible facial emotion].",
                  "dialogue": "Spoken lines of characters heard in this time window, formatted as 'Speaker: line' joined by ' / '. Use the character names from the character sheet, or 'Unknown' if the speaker cannot be identified. Use an empty string if nobody speaks. $transcriptInstruction"
                }
            """.trimIndent()
            NarrationMode.NARASI_ASLI -> """
                {
                  "timestamp": "MM:SS - MM:SS",
                  "sceneDescription": "Short, vivid description of the scene in natural American English.",
                  "visualPrompt": "$stylePrompt. [Subject: name and exact established appearance], [Action: specific physical movement or stance], [Camera: framing, angle, and focus], [Lighting: source, direction, shadows, and highlights], [Mood: emotional atmosphere], [Background: environment, architecture, and relevant details], [Expression: visible facial emotion].",
                  "originalNarration": "Only the off-screen narrator's voice-over heard in this time window, not character dialogue. Use an empty string if there is no narrator. $transcriptInstruction"
                }
            """.trimIndent()
            NarrationMode.REKAP_VOICEOVER -> """
                {
                  "timestamp": "MM:SS - MM:SS",
                  "sceneDescription": "Short, vivid description of the scene in natural American English.",
                  "visualPrompt": "$stylePrompt. [Subject: name and exact established appearance], [Action: specific physical movement or stance], [Camera: framing, angle, and focus], [Lighting: source, direction, shadows, and highlights], [Mood: emotional atmosphere], [Background: environment, architecture, and relevant details], [Expression: visible facial emotion]."
                }
            """.trimIndent()
        }

        val languageBlock = if (mode == NarrationMode.REKAP_VOICEOVER) {
            """
            LANGUAGE:
            - ALL output text (summary, descriptions, visual prompts, and character sheet) MUST be written in natural American English.
            """.trimIndent()
        } else if (language == NarrationLanguage.ORIGINAL) {
            """
            LANGUAGE (STRICT):
            - Transcript fields ('dialogue' and 'originalNarration') MUST be written in the ORIGINAL spoken language of the audio. Do NOT translate them.
            - ALL other fields ('segmentSummary', 'characterSheet', 'sceneDescription', and 'visualPrompt') MUST be written in natural American English.
            """.trimIndent()
        } else {
            """
            LANGUAGE (STRICT):
            - The output language is $targetLanguage. This applies to the transcript fields 'dialogue' and 'originalNarration'.
            - ALL other fields ('segmentSummary', 'characterSheet', 'sceneDescription', and 'visualPrompt') MUST be written in natural American English.
            - The audio in the video may be in a different language than $targetLanguage. You MUST translate everything you hear into $targetLanguage. Never copy the original spoken language.
            - Only proper nouns (character names, place names, technique names) stay unchanged.
            """.trimIndent()
        }

        val finalCheckReminder = when {
            mode == NarrationMode.REKAP_VOICEOVER -> ""
            language == NarrationLanguage.ORIGINAL -> "Final check: 'dialogue' and 'originalNarration' must be in the original spoken language, never translated.\n"
            else -> "Final check: every 'dialogue' and 'originalNarration' value must be written in $targetLanguage.\n"
        }

        return """
            You are an expert anime visual director and storyboard analyst.
            Analyze the provided video segment between $startFormatted and $endFormatted.
            
            $timestampsBlock
            
            $languageBlock

            ${if (speechActivityBlock.isNotBlank()) "$speechActivityBlock\n\n            " else ""}
            
            ${if (seriesBibleBlock.isNotBlank()) "$seriesBibleBlock\n\n            " else ""}$characterContext
            
            OUTPUT JSON FORMAT:
            ${finalCheckReminder}Return ONLY a valid JSON object with exactly this schema:
            {
              "segmentSummary": "A concise, fluid 2-3 sentence story summary of what happened in this 3-minute segment in natural American English.",
              "characterSheet": [
                {
                  "name": "Character Name",
                  "description": "Hair color/style, eye color/shape, facial features, exact signature anime attire, color palette, accessories."
                }
              ],
              "timeline": [
$timelineItemSchema
              ]
            }
            
            CRITICAL VISUAL PROMPT RULES:
            1. Every visualPrompt MUST strictly start with: "$stylePrompt". Follow only the selected style; do not mix other styles.
            2. Repeat the character's exact physical descriptions from the character sheet inside each visual prompt so text-to-image models maintain character consistency across shots.
            3. Every visual prompt must be one paragraph in this order, with every labeled part present: [Subject], [Action], [Camera], [Lighting], [Mood], [Background]. Add [Expression] when a face is visible. Repeat stable physical appearance in [Subject] and keep it consistent with the character sheet.
            4. Do NOT mention any animation studio names or artist names.
        """.trimIndent()
    }

    fun buildFinalNarrationPrompt(
        segmentSummaries: List<String>,
        characterSheet: List<CharacterInfo>,
        language: NarrationLanguage,
        previousRecapContext: String?
    ): String {
        val summariesText = segmentSummaries.mapIndexed { idx, sum ->
            "Segment ${idx + 1}: $sum"
        }.joinToString("\n\n")

        val charactersText = characterSheet.joinToString("\n") {
            "- ${it.name}: ${it.description}"
        }

        val previousRecapInstruction = if (!previousRecapContext.isNullOrBlank()) {
            if (language == NarrationLanguage.INDONESIAN) {
                """
                PREVIOUS STORYLINE CONTEXT FROM SERIES BIBLE:
                $previousRecapContext

                OPENING RECAP INSTRUCTION ("SEBELUMNYA DI..."):
                - Mulai narasi secara alami dengan 2 sampai 3 kalimat "sebelumnya..." yang merangkum peristiwa penting dari cerita sebelumnya (diambil dari konteks di atas).
                - Tulis dalam Bahasa Indonesia alami, hidup, dan bebas spoiler berlebihan untuk episode sekarang.
                - Setelah 2-3 kalimat kilas balik tersebut, lanjutkan narasi langsung mengalir ke peristiwa episode ini.
                """.trimIndent()
            } else {
                """
                PREVIOUS STORYLINE CONTEXT FROM SERIES BIBLE:
                $previousRecapContext

                OPENING RECAP INSTRUCTION ("PREVIOUSLY ON..."):
                - Begin the narration naturally with 2 to 3 concise "previously..." sentences summarizing relevant past events (drawn from the context above).
                - Write in natural American English, completely spoiler-free for current events.
                - After those 2-3 opening recap sentences, seamlessly transition directly into the events of this episode.
                """.trimIndent()
            }
        } else {
            ""
        }

        val instructionBlock = if (language == NarrationLanguage.INDONESIAN) {
            """
            INSTRUCTIONS:
            Write an engaging, fluid, and captivating story recap of the entire episode in natural, highly expressive Bahasa Indonesia (gaya pencerita anime yang hidup, emosional, dramatis, dan mengalir natural, bukan terjemahan harfiah/kaku).
            - Gunakan Bahasa Indonesia yang sopan, hidup, dan natural, tanpa umpatan, kata kasar, atau hinaan.
            - Form an organic narrative arc capturing character motives, rising action, emotional drama, visual tension, and the climax.
            - Keep official character names and terms intact.
            """.trimIndent()
        } else {
            """
            INSTRUCTIONS:
            Write an engaging, fluid, and captivating story recap of the entire episode in natural American English (native USA storytelling voice).
            - Form an organic narrative arc capturing character motives, rising action, emotional drama, visual tension, and the climax.
            - Keep official character names and terms intact.
            """.trimIndent()
        }

        val introHint = if (language == NarrationLanguage.INDONESIAN) {
            "e.g. do NOT include \"Berikut adalah rekap...\", \"Here is the recap...\", or similar phrases"
        } else {
            "e.g. do NOT include \"Here is the recap...\", \"Here is the story...\", or similar opening remarks"
        }

        val formatBlock = """
        VOICE-OVER OUTPUT FORMAT:
        - Provide output ready for voice-over: clean prose paragraphs with short-to-medium sentences.
        - Exactly 3 to 5 flowing paragraphs.
        - Absolutely NO markdown (no asterisks, no bold/italics), NO titles or headings, NO emojis, NO parentheses, NO sound effects, and NO timestamps.
        - Absolutely NO conversational intro or greeting ($introHint). Start directly with the narration.
        """.trimIndent()

        return """
        You are a master anime storyteller and narrator.
        Based on the chronological progression of this complete anime episode:

        CHRONOLOGICAL SEGMENTS:
        $summariesText

        KEY CHARACTERS:
        $charactersText

        ${if (previousRecapInstruction.isNotBlank()) "$previousRecapInstruction\n\n        " else ""}$instructionBlock

        $youtubeSafeRules

        $formatBlock
        """.trimIndent()
    }

    fun buildMergeNarrationsPrompt(
        parts: List<Pair<String, String>>,
        language: NarrationLanguage
    ): String {
        val partsText = parts.mapIndexed { idx, (title, text) ->
            "--- BAGIAN ${idx + 1}: $title ---\n$text"
        }.joinToString("\n\n")

        val instructionBlock = if (language == NarrationLanguage.INDONESIAN) {
            """
            INSTRUCTIONS:
            Gabungkan narasi dari setiap bagian di atas menjadi satu kesatuan narasi yang utuh, dinamis, dan mengalir mulus.
            - Tambahkan transisi cerita yang luwes dan alami saat berpindah antar bagian, menghubungkan alur secara logis dan emosional.
            - JANGAN MENAMBAH KEJADIAN BARU, fakta fiktif baru, atau spekulasi yang tidak ada pada bagian-bagian tersebut.
            - Gunakan Bahasa Indonesia yang sopan, hidup, dan natural, tanpa umpatan atau kata kasar.
            - Pertahankan nama karakter dan istilah penting tetap konsisten.
            """.trimIndent()
        } else {
            """
            INSTRUCTIONS:
            Seamlessly combine the story narrations from each section above into a single, cohesive, unified narrative arc.
            - Add natural and fluid narrative transitions between sections so the story flows organically.
            - DO NOT INVENT NEW EVENTS, scenes, or facts not present in the provided sections.
            - Write in natural American English storytelling voice.
            - Keep character names and established terms consistent.
            """.trimIndent()
        }

        val formatBlock = """
        OUTPUT FORMAT:
        - Output ready for voice-over / reading: clean prose paragraphs.
        - Absolutely NO markdown (no asterisks, no bold/italics), NO titles or headings (do not include 'Bagian 1', etc.), NO emojis, NO parentheses, NO sound effects, and NO timestamps.
        - Absolutely NO conversational greeting or intro (do NOT write 'Berikut adalah...', 'Here is the combined story...', etc.). Start immediately with the narration text.
        """.trimIndent()

        return """
        You are a master anime storyteller and narrator.
        Combine and harmonize the following story sections from consecutive episodes into one seamless continuous narrative:

        $partsText

        $instructionBlock

        $youtubeSafeRules

        $formatBlock
        """.trimIndent()
    }

    fun buildSeriesBiblePrompt(
        previousBible: SeriesBible?,
        segmentSummaries: List<String>,
        characterSheet: List<CharacterInfo>,
        videoName: String
    ): String {
        val previousBibleText = if (previousBible != null && !previousBible.isEmpty()) {
            SeriesBibleStorage.toJson(previousBible)
        } else {
            "None (this is the first episode in the series)."
        }

        val summariesText = segmentSummaries.mapIndexed { idx, sum ->
            "Segment ${idx + 1}: $sum"
        }.joinToString("\n\n")

        val charactersText = characterSheet.joinToString("\n") {
            "- ${it.name}: ${it.description}"
        }

        return """
        You are an expert anime continuity editor and series bible archivist.
        Your task is to update the cumulative "Series Bible" (world lore, terminology, character dynamics, and overarching plot) by integrating the events of this newly concluded episode with any established continuity lore from previous episodes.

        CURRENT EPISODE TITLE / IDENTIFIER:
        $videoName

        PREVIOUS SERIES BIBLE (IF ANY):
        $previousBibleText

        THIS EPISODE'S CHRONOLOGICAL SEGMENT SUMMARIES:
        $summariesText

        ESTABLISHED CHARACTERS:
        $charactersText

        INSTRUCTIONS:
        1. LOCATIONS: Merge previous locations with any newly introduced or expanded key locations. Deduplicate names (case-insensitive). Keep descriptions concise and distinct. Retain established lore. Max 20 locations.
        2. GLOSSARY: Merge previous terminology, abilities, titles, magic systems, technology, or lore terms. Retain official naming, spellings, and concise definitions. Max 30 terms.
        3. RELATIONSHIPS: Merge and update character dynamics (e.g. rival, mentor, allied, estranged). Max 20 relationships.
        4. PLOT SO FAR: Synthesize an updated, cumulative high-level summary of the entire series story up through this episode. Keep it concise, focused on key stakes and milestones, free of excessive minutiae or spoilers beyond this point. MUST be strictly under 1200 characters.
        5. EPISODE RECAP: Write a tight 2 to 3 sentence recap summarizing specifically what occurred in THIS episode ($videoName). Under 300 characters.
        6. General rule: do not invent facts; only record what is established in the summaries, character sheet, or existing bible.

        OUTPUT FORMAT:
        Return ONLY valid JSON (no markdown formatting, no code fences, no extra commentary):
        {
          "locations": [
            { "name": "Location Name", "description": "Brief description" }
          ],
          "glossary": [
            { "term": "Term/Concept", "definition": "Brief definition" }
          ],
          "relationships": [
            { "characterA": "Name A", "characterB": "Name B", "relationship": "Dynamic" }
          ],
          "plotSoFar": "Cumulative high-level summary up through this episode under 1200 chars.",
          "episodeRecap": "2-3 sentences summarizing this specific episode."
        }
        """.trimIndent()
    }

    fun cleanJsonString(text: String): String {
        val trimmed = text.trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        return if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            trimmed.substring(firstBrace, lastBrace + 1)
        } else {
            trimmed
        }
    }

    fun parseSegmentOutput(tag: String, stage: String, rawJson: String): SegmentAnalysisOutput {
        try {
            val json = JSONObject(rawJson)
            val candidates = json.optJSONArray("candidates")
            val rawText = if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    parts.getJSONObject(0).optString("text", "")
                } else ""
            } else {
                rawJson
            }

            val cleaned = cleanJsonString(rawText)
            val parsed = JSONObject(cleaned)

            val segmentSummary = parsed.optString("segmentSummary", "Summary of anime segment.")

            val characterSheetList = mutableListOf<CharacterInfo>()
            val charactersArray = parsed.optJSONArray("characterSheet")
            if (charactersArray != null) {
                for (i in 0 until charactersArray.length()) {
                    val item = charactersArray.optJSONObject(i) ?: continue
                    val name = item.optString("name", "Unknown Character")
                    val desc = item.optString("description", "")
                    if (name.isNotBlank()) {
                        characterSheetList.add(CharacterInfo(name = name, description = desc))
                    }
                }
            }

            val timelineList = mutableListOf<TimelineItem>()
            val timelineArray = parsed.optJSONArray("timeline")
            if (timelineArray != null) {
                for (i in 0 until timelineArray.length()) {
                    val item = timelineArray.optJSONObject(i) ?: continue
                    val timestamp = item.optString("timestamp", "")
                    val sceneDescription = item.optString("sceneDescription", "")
                    val visualPrompt = item.optString("visualPrompt", "")
                    val dialogue = item.optString("dialogue", "")
                    val originalNarration = item.optString("originalNarration", "")
                    if (timestamp.isNotBlank() || sceneDescription.isNotBlank()) {
                        timelineList.add(
                            TimelineItem(
                                timestamp = timestamp,
                                sceneDescription = sceneDescription,
                                visualPrompt = visualPrompt,
                                dialogue = dialogue,
                                originalNarration = originalNarration
                            )
                        )
                    }
                }
            }

            return SegmentAnalysisOutput(
                segmentSummary = segmentSummary,
                characterSheet = characterSheetList,
                timeline = timelineList
            )
        } catch (e: Exception) {
            Log.e(tag, "[$stage] Gagal memparsing respons JSON (${rawJson.length} karakter): ${e.javaClass.simpleName}.")
            throw GeminiApiException(
                stage = stage,
                httpCode = 200,
                rawMessage = "Gagal memproses JSON respons: ${e.message}\nRespons Mentah:\n$rawJson",
                userFriendlyMessage = "Format JSON hasil analisis tidak sesuai: ${e.message}",
                cause = e
            )
        }
    }

    fun parseTextCandidate(
        stage: String,
        rawResponse: String,
        parseErrorMessage: String
    ): String {
        try {
            val json = JSONObject(rawResponse)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text", "").trim()
                }
            }
            throw GeminiApiException(
                stage = stage,
                httpCode = 200,
                rawMessage = rawResponse,
                userFriendlyMessage = "Respons dari AI tidak memuat kandidat teks narasi."
            )
        } catch (e: GeminiApiException) {
            throw e
        } catch (e: Exception) {
            throw GeminiApiException(
                stage = stage,
                httpCode = 200,
                rawMessage = "Gagal memproses JSON respons: ${e.message}\nRespons Mentah:\n$rawResponse",
                userFriendlyMessage = parseErrorMessage,
                cause = e
            )
        }
    }

    fun parseSeriesBibleOutput(
        tag: String,
        stage: String,
        rawResponse: String,
        previousBible: SeriesBible?,
        videoName: String
    ): SeriesBible? {
        return try {
            val json = JSONObject(rawResponse)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "").trim()
                    val parsedBible = SeriesBibleStorage.fromJson(text)
                    if (parsedBible != null) {
                        var cleaned = text
                        if (cleaned.startsWith("```json", ignoreCase = true)) cleaned = cleaned.substring(7)
                        else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3)
                        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length - 3)
                        val textJson = JSONObject(cleaned.trim())
                        val episodeRecap = textJson.optString("episodeRecap", "").trim().take(SeriesBible.MAX_EPISODE_RECAP_CHARS)

                        val finalBible = parsedBible
                            .copy(episodes = previousBible?.episodes.orEmpty())
                            .withUpdatedEpisode(videoName, episodeRecap)
                        Log.i(tag, "[$stage] Memori seri berhasil diperbarui (${finalBible.locations.size} lokasi, ${finalBible.glossary.size} istilah, ${finalBible.relationships.size} relasi, ${finalBible.episodes.size} episode)")
                        return finalBible
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(tag, "[$stage] Gagal membaca atau memproses respons memori seri (${e.javaClass.simpleName}).")
            null
        }
    }
}
