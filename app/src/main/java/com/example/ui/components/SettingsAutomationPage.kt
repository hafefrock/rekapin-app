package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

@Composable
internal fun SettingsSeriesAutomationPage(
    isAutoBuildSeriesBible: Boolean,
    onToggleAutoBuildSeriesBible: ((Boolean) -> Unit)?,
    isPrependRecapNarration: Boolean,
    onTogglePrependRecapNarration: ((Boolean) -> Unit)?,
    isAutoSkipOpEd: Boolean,
    onToggleAutoSkipOpEd: ((Boolean) -> Unit)?,
    isDetectRepeatedScenes: Boolean,
    onToggleDetectRepeatedScenes: ((Boolean) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.md),
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(
                title = "Memori Seri",
                titleStyle = RekapinTypography.bodyBold
            )
            RekapinCard(modifier = Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "Bangun memori seri otomatis",
                    subtitle = "+1 permintaan Gemini per analisis",
                    modifier = Modifier.padding(
                        horizontal = RekapinSpacing.md,
                        vertical = 6.dp
                    ),
                    control = {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                            Switch(
                                checked = isAutoBuildSeriesBible,
                                onCheckedChange = { onToggleAutoBuildSeriesBible?.invoke(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("switch_auto_build_series_bible")
                            )
                        }
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = RekapinSpacing.md)
                )
                SettingRow(
                    title = "Tambahkan recap 'Sebelumnya...' di awal narasi",
                    subtitle = "Kilas balik 2 sampai 3 kalimat di awal narasi (bebas spoiler)",
                    modifier = Modifier.padding(
                        horizontal = RekapinSpacing.md,
                        vertical = 6.dp
                    ),
                    control = {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                            Switch(
                                checked = isPrependRecapNarration,
                                onCheckedChange = { onTogglePrependRecapNarration?.invoke(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("switch_prepend_recap_narration")
                            )
                        }
                    }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(
                title = "Deteksi Lokal",
                titleStyle = RekapinTypography.bodyBold
            )
            RekapinCard(modifier = Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "Lewati OP/ED otomatis (hanya saat Seri dipilih)",
                    subtitle = "Potong OP/ED dari rentang analisis (lokal, tanpa kuota)",
                    modifier = Modifier.padding(
                        horizontal = RekapinSpacing.md,
                        vertical = 6.dp
                    ),
                    control = {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                            Switch(
                                checked = isAutoSkipOpEd,
                                onCheckedChange = { onToggleAutoSkipOpEd?.invoke(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("switch_auto_skip_op_ed")
                            )
                        }
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = RekapinSpacing.md)
                )
                SettingRow(
                    title = "Cari adegan berulang (menambah waktu persiapan)",
                    subtitle = "Deteksi flashback atau adegan berulang (lokal, menambah waktu)",
                    modifier = Modifier.padding(
                        horizontal = RekapinSpacing.md,
                        vertical = 6.dp
                    ),
                    control = {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                            Switch(
                                checked = isDetectRepeatedScenes,
                                onCheckedChange = { onToggleDetectRepeatedScenes?.invoke(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("switch_detect_repeated_scenes")
                            )
                        }
                    }
                )
            }
        }
    }
}
