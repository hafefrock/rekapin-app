package com.example.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sistem Desain Rekapin:
 * - Spasi: 4 / 8 / 12 / 16 / 24 / 32 dp
 * - Radius sudut: 8 / 12 / 16 dp
 * - Lebar border terpilih: 1.5 dp
 * - Skala tipografi:
 *     - Judul layar: 22sp bold
 *     - Judul bagian: 16sp semibold
 *     - Isi: 14sp
 *     - Keterangan: 12sp onSurfaceVariant
 */
object RekapinSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}

object RekapinRadii {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
}

object RekapinBorders {
    val selectedWidth: Dp = 1.5.dp
}

object RekapinTypography {
    val screenTitle: TextStyle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    )

    val sectionTitle: TextStyle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp
    )

    val body: TextStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    )

    val bodyMedium: TextStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp
    )

    val bodyBold: TextStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp
    )

    val caption: TextStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )

    val captionMedium: TextStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    )

    val captionBold: TextStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp
    )
}
