package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RekapinBorders
import com.example.ui.theme.RekapinRadii
import com.example.ui.theme.RekapinSpacing
import com.example.ui.theme.RekapinTypography

/**
 * Kartu datar tanpa bayangan, memanfaatkan variasi kontras warna permukaan gunmetal.
 * Menampilkan border oranye 1.5 dp hanya bila berstatus terpilih.
 */
@Composable
fun RekapinCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(RekapinRadii.md),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = if (isSelected) {
        BorderStroke(RekapinBorders.selectedWidth, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = border,
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = border,
            content = content
        )
    }
}

/**
 * Header bagian dengan judul 16sp semibold, keterangan 12sp onSurfaceVariant,
 * dan slot aksi opsional di sisi kanan.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleStyle: TextStyle = RekapinTypography.sectionTitle,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = RekapinTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            Spacer(modifier = Modifier.width(RekapinSpacing.sm))
            action()
        }
    }
}

/**
 * Baris pengaturan konsisten: judul & keterangan di kiri, kontrol di kanan.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    control: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = RekapinTypography.bodyBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(RekapinSpacing.md))
        control()
    }
}

/**
 * Satu baris chip horizontal yang bisa digulir, pengganti radio button atau kartu panjang.
 * Chip terpilih menampilkan border aksen oranye 1.5 dp dan warna teks oranye.
 */
@Composable
fun <T> ChoiceChips(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    subtitleProvider: ((T) -> String)? = null,
    testTagProvider: ((T) -> String)? = null,
    compact: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else RekapinSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = (item == selectedItem)
            val label = labelProvider(item)
            val subtitle = if (compact) null else subtitleProvider?.invoke(item)
            val tag = testTagProvider?.invoke(item)

            val chipModifier = Modifier
                .then(if (compact) Modifier.height(32.dp) else Modifier)
                .clip(RoundedCornerShape(RekapinRadii.sm))
                .clickable { onItemSelected(item) }
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)

            Surface(
                shape = RoundedCornerShape(RekapinRadii.sm),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                border = if (isSelected) {
                    BorderStroke(RekapinBorders.selectedWidth, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                modifier = chipModifier
            ) {
                if (compact) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = (if (isSelected) RekapinTypography.captionBold else RekapinTypography.captionMedium)
                                .copy(fontSize = 12.sp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = label,
                            style = if (isSelected) RekapinTypography.bodyBold else RekapinTypography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = RekapinTypography.caption,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bagian yang bisa dilipat (Collapsible):
 * Menampilkan ringkasan singkat saat tertutup, dan isi lengkap saat dibuka.
 */
@Composable
fun CollapsibleSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    titleStyle: TextStyle = RekapinTypography.sectionTitle,
    headerVerticalPadding: Dp = RekapinSpacing.sm,
    showSummaryWhenExpanded: Boolean = false,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrowRotation")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RekapinRadii.sm))
                .clickable { onToggle() }
                .padding(vertical = headerVerticalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if ((showSummaryWhenExpanded || !isExpanded) && !summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = RekapinTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (headerAction != null) {
                headerAction()
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Tutup" else "Buka",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = RekapinSpacing.sm),
                content = content
            )
        }
    }
}

/**
 * Action bar utama yang menempel di bawah dalam satu baris kompak:
 * ringkasan status di kiri dan tombol aksi 40 dp di kanan.
 * Jika summaryText kosong, tombol tampil selebar layar.
 */
@Composable
fun PrimaryActionBar(
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    enabled: Boolean = true,
    summaryText: String? = null,
    icon: ImageVector? = null
) {
    val hasSummary = !summaryText.isNullOrBlank()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RekapinSpacing.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.md)
        ) {
            if (hasSummary) {
                Text(
                    text = summaryText!!,
                    style = RekapinTypography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(RekapinRadii.md),
                contentPadding = PaddingValues(horizontal = RekapinSpacing.lg, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = buttonModifier
                    .then(
                        if (hasSummary) Modifier.widthIn(min = 150.dp)
                        else Modifier.fillMaxWidth()
                    )
                    .height(40.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(RekapinSpacing.sm))
                }
                Text(
                    text = buttonText,
                    style = RekapinTypography.bodyBold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Baris pilihan dengan RadioButton di kiri untuk diletakkan di dalam satu RekapinCard.
 * Baris terpilih memakai latar tonal oranye tipis.
 */
@Composable
fun RekapinRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radioModifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    badgeText: String? = null,
    warningText: String? = null
) {
    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val contentAlpha = if (enabled) 1f else 0.5f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(backgroundColor)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = RekapinSpacing.md, vertical = RekapinSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
            androidx.compose.material3.RadioButton(
                selected = selected,
                onClick = if (enabled) onClick else null,
                enabled = enabled,
                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                ),
                modifier = radioModifier
            )
        }
        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RekapinSpacing.sm)
            ) {
                Text(
                    text = title,
                    style = RekapinTypography.bodyBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                if (!badgeText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(RekapinRadii.sm))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                            .padding(horizontal = RekapinSpacing.sm, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = RekapinTypography.caption.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!warningText.isNullOrBlank()) {
                Text(
                    text = warningText,
                    style = RekapinTypography.captionMedium.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * Kolom isian kompak: label caption di atas, teks 14sp, tinggi 40 dp untuk satu baris,
 * border 1 dp oranye saat fokus dan merah saat error.
 */
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val isSingleLineBox = singleLine && minLines == 1

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(RekapinSpacing.xs)
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = RekapinTypography.caption.copy(fontSize = 11.sp),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = RekapinTypography.body.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = fieldModifier
                .fillMaxWidth()
                .then(
                    if (isSingleLineBox) {
                        Modifier.height(40.dp)
                    } else {
                        Modifier.heightIn(min = (minLines * 20 + 16).dp)
                    }
                )
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isSingleLineBox) Modifier.fillMaxHeight() else Modifier)
                        .clip(RoundedCornerShape(RekapinRadii.sm))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(RekapinRadii.sm)
                        )
                        .padding(
                            horizontal = RekapinSpacing.sm,
                            vertical = if (isSingleLineBox) 0.dp else RekapinSpacing.sm
                        ),
                    verticalAlignment = if (isSingleLineBox) Alignment.CenterVertically else Alignment.Top
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = if (isSingleLineBox) Alignment.CenterStart else Alignment.TopStart
                    ) {
                        if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                            Text(
                                text = placeholder,
                                style = RekapinTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = if (isSingleLineBox) 1 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        Spacer(modifier = Modifier.width(RekapinSpacing.xs))
                        trailingIcon()
                    }
                }
            }
        )

        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = RekapinTypography.caption.copy(fontSize = 11.sp),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


