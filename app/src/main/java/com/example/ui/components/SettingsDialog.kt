package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ApiKeyItem
import com.example.data.model.NarrationLanguage
import com.example.data.model.NarrationMode
import com.example.data.model.RequestPacingMode
import com.example.data.model.ThinkingLevelMode
import com.example.data.model.VisualStyle
import com.example.data.prefs.ModelUsageStorage
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

@Composable
fun SettingsDialog(
    apiKeys: List<ApiKeyItem>,
    activeKeyName: String,
    models: List<String>,
    selectedModel: String,
    isAutoSwitchModel: Boolean,
    selectedLanguage: NarrationLanguage,
    selectedNarrationMode: NarrationMode = NarrationMode.DEFAULT,
    onDismiss: () -> Unit,
    onAddKey: (name: String, key: String) -> Unit,
    onRenameKey: (id: String, newName: String) -> Unit,
    onDeleteKey: (id: String) -> Unit,
    onSetActiveKey: (id: String) -> Unit,
    onAddModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onMoveModel: (fromIndex: Int, toIndex: Int) -> Unit,
    onSelectModel: (String) -> Unit,
    onToggleAutoSwitch: (Boolean) -> Unit,
    onSelectLanguage: (NarrationLanguage) -> Unit,
    onSelectNarrationMode: ((NarrationMode) -> Unit)? = null,
    visualStyles: List<VisualStyle> = emptyList(),
    selectedStyle: VisualStyle? = null,
    onSelectStyle: ((VisualStyle) -> Unit)? = null,
    onAddCustomStyle: ((name: String, promptDescription: String) -> Unit)? = null,
    onUpdateCustomStyle: ((id: String, name: String, promptDescription: String) -> Unit)? = null,
    onDeleteCustomStyle: ((id: String) -> Unit)? = null,
    modelUsageStorage: ModelUsageStorage? = null,
    onSetModelLimit: ((model: String, limit: Int) -> Unit)? = null,
    onSetModelUsage: ((model: String, usage: Int) -> Unit)? = null,
    onTestKey: (suspend (key: String) -> Pair<Boolean, String>)? = null,
    isAutoBuildSeriesBible: Boolean = true,
    onToggleAutoBuildSeriesBible: ((Boolean) -> Unit)? = null,
    isPrependRecapNarration: Boolean = false,
    onTogglePrependRecapNarration: ((Boolean) -> Unit)? = null,
    isAutoSkipOpEd: Boolean = true,
    onToggleAutoSkipOpEd: ((Boolean) -> Unit)? = null,
    isDetectRepeatedScenes: Boolean = true,
    onToggleDetectRepeatedScenes: ((Boolean) -> Unit)? = null,
    isKeyDecryptFailed: Boolean = false,
    onTestKeyWithProgress: (suspend (key: String, onProgress: (String) -> Unit) -> Pair<Boolean, String>)? = null,
    selectedPacingMode: RequestPacingMode = RequestPacingMode.DEFAULT,
    onSelectPacingMode: ((RequestPacingMode) -> Unit)? = null,
    selectedThinkingLevel: ThinkingLevelMode = ThinkingLevelMode.DEFAULT,
    onSelectThinkingLevel: ((ThinkingLevelMode) -> Unit)? = null
) {
    var selectedCategory by rememberSaveable { mutableStateOf<Int?>(null) }
    val pageStateHolder = rememberSaveableStateHolder()

    val handleBack: () -> Unit = {
        if (selectedCategory != null) {
            selectedCategory = null
        } else {
            onDismiss()
        }
    }

    val categoryTitle = when (selectedCategory) {
        0 -> "Kunci API"
        1 -> "Model"
        2 -> "Bahasa dan Mode Narasi"
        3 -> "Gaya Visual"
        4 -> "Seri dan Otomatisasi"
        else -> "Pengaturan"
    }

    Dialog(
        onDismissRequest = handleBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = handleBack)

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (selectedCategory != null) RekapinSpacing.xs else RekapinSpacing.md,
                                end = RekapinSpacing.xs,
                                top = 6.dp,
                                bottom = 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedCategory != null) {
                            IconButton(
                                onClick = { selectedCategory = null },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kembali",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                        }
                        Text(
                            text = categoryTitle,
                            style = if (selectedCategory == null) {
                                RekapinTypography.sectionTitle.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                RekapinTypography.bodyBold.copy(fontSize = 16.sp)
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_close_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    pageStateHolder.SaveableStateProvider(key = selectedCategory ?: -1) {
                        when (selectedCategory) {
                            null -> SettingsCategoryOverview(
                                apiKeys = apiKeys,
                                activeKeyName = activeKeyName,
                                isKeyDecryptFailed = isKeyDecryptFailed,
                                selectedModel = selectedModel,
                                isAutoSwitchModel = isAutoSwitchModel,
                                selectedLanguage = selectedLanguage,
                                selectedNarrationMode = selectedNarrationMode,
                                visualStyles = visualStyles,
                                selectedStyle = selectedStyle,
                                isAutoBuildSeriesBible = isAutoBuildSeriesBible,
                                isPrependRecapNarration = isPrependRecapNarration,
                                isAutoSkipOpEd = isAutoSkipOpEd,
                                isDetectRepeatedScenes = isDetectRepeatedScenes,
                                onSelectCategory = { selectedCategory = it }
                            )
                            0 -> SettingsApiKeyPage(
                                apiKeys = apiKeys,
                                isKeyDecryptFailed = isKeyDecryptFailed,
                                onAddKey = onAddKey,
                                onRenameKey = onRenameKey,
                                onDeleteKey = onDeleteKey,
                                onSetActiveKey = onSetActiveKey,
                                onTestKey = onTestKey,
                                onTestKeyWithProgress = onTestKeyWithProgress
                            )
                            1 -> SettingsModelPage(
                                models = models,
                                selectedModel = selectedModel,
                                isAutoSwitchModel = isAutoSwitchModel,
                                onToggleAutoSwitch = onToggleAutoSwitch,
                                selectedPacingMode = selectedPacingMode,
                                onSelectPacingMode = onSelectPacingMode,
                                selectedThinkingLevel = selectedThinkingLevel,
                                onSelectThinkingLevel = onSelectThinkingLevel,
                                onAddModel = onAddModel,
                                onDeleteModel = onDeleteModel,
                                onMoveModel = onMoveModel,
                                onSelectModel = onSelectModel,
                                modelUsageStorage = modelUsageStorage,
                                onSetModelLimit = onSetModelLimit,
                                onSetModelUsage = onSetModelUsage
                            )
                            2 -> SettingsLanguageAndModePage(
                                selectedLanguage = selectedLanguage,
                                selectedNarrationMode = selectedNarrationMode,
                                onSelectLanguage = onSelectLanguage,
                                onSelectNarrationMode = onSelectNarrationMode
                            )
                            3 -> SettingsVisualStylesPage(
                                visualStyles = visualStyles,
                                selectedStyle = selectedStyle,
                                onSelectStyle = onSelectStyle,
                                onAddCustomStyle = onAddCustomStyle,
                                onUpdateCustomStyle = onUpdateCustomStyle,
                                onDeleteCustomStyle = onDeleteCustomStyle
                            )
                            4 -> SettingsSeriesAutomationPage(
                                isAutoBuildSeriesBible = isAutoBuildSeriesBible,
                                onToggleAutoBuildSeriesBible = onToggleAutoBuildSeriesBible,
                                isPrependRecapNarration = isPrependRecapNarration,
                                onTogglePrependRecapNarration = onTogglePrependRecapNarration,
                                isAutoSkipOpEd = isAutoSkipOpEd,
                                onToggleAutoSkipOpEd = onToggleAutoSkipOpEd,
                                isDetectRepeatedScenes = isDetectRepeatedScenes,
                                onToggleDetectRepeatedScenes = onToggleDetectRepeatedScenes
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryOverview(
    apiKeys: List<ApiKeyItem>,
    activeKeyName: String,
    isKeyDecryptFailed: Boolean,
    selectedModel: String,
    isAutoSwitchModel: Boolean,
    selectedLanguage: NarrationLanguage,
    selectedNarrationMode: NarrationMode,
    visualStyles: List<VisualStyle>,
    selectedStyle: VisualStyle?,
    isAutoBuildSeriesBible: Boolean,
    isPrependRecapNarration: Boolean,
    isAutoSkipOpEd: Boolean,
    isDetectRepeatedScenes: Boolean,
    onSelectCategory: (Int) -> Unit
) {
    val apiKeySummary = when {
        isKeyDecryptFailed -> "Perlu dimasukkan ulang"
        apiKeys.isEmpty() -> "Belum ada key"
        else -> "${apiKeys.size} key · aktif: $activeKeyName"
    }
    val modelSummary =
        "$selectedModel · pindah otomatis ${if (isAutoSwitchModel) "aktif" else "nonaktif"}"
    val languageSummary =
        "${selectedLanguage.displayName} · ${selectedNarrationMode.displayName}"
    val styleSummary =
        "${selectedStyle?.name ?: "-"} · ${visualStyles.size} gaya"
    val activeAutomationCount = listOf(
        isAutoBuildSeriesBible,
        isPrependRecapNarration,
        isAutoSkipOpEd,
        isDetectRepeatedScenes
    ).count { it }
    val automationSummary = "$activeAutomationCount dari 4 aktif"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.md),
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(
                title = "Akses Gemini",
                titleStyle = RekapinTypography.bodyBold
            )
            RekapinCard(modifier = Modifier.fillMaxWidth()) {
                SettingsCategoryRow(
                    icon = Icons.Default.Key,
                    title = "Kunci API",
                    summary = apiKeySummary,
                    isError = isKeyDecryptFailed,
                    onClick = { onSelectCategory(0) },
                    modifier = Modifier.testTag("tab_settings_api_keys")
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = RekapinSpacing.md)
                )
                SettingsCategoryRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Model",
                    summary = modelSummary,
                    onClick = { onSelectCategory(1) },
                    modifier = Modifier.testTag("tab_settings_models")
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(
                title = "Hasil Analisis",
                titleStyle = RekapinTypography.bodyBold
            )
            RekapinCard(modifier = Modifier.fillMaxWidth()) {
                SettingsCategoryRow(
                    icon = Icons.Default.Language,
                    title = "Bahasa dan Mode Narasi",
                    summary = languageSummary,
                    onClick = { onSelectCategory(2) },
                    modifier = Modifier.testTag("tab_settings_language")
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = RekapinSpacing.md)
                )
                SettingsCategoryRow(
                    icon = Icons.Default.Palette,
                    title = "Gaya Visual",
                    summary = styleSummary,
                    onClick = { onSelectCategory(3) },
                    modifier = Modifier.testTag("tab_settings_visual_styles")
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(
                title = "Otomatisasi",
                titleStyle = RekapinTypography.bodyBold
            )
            RekapinCard(modifier = Modifier.fillMaxWidth()) {
                SettingsCategoryRow(
                    icon = Icons.Default.Bookmark,
                    title = "Seri dan Otomatisasi",
                    summary = automationSummary,
                    onClick = { onSelectCategory(4) },
                    modifier = Modifier.testTag("tab_settings_series_bible")
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(RekapinRadii.sm))
                .background(
                    if (isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(RekapinSpacing.md))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = RekapinTypography.bodyBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isError) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
                Text(
                    text = summary,
                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(RekapinSpacing.sm))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}
