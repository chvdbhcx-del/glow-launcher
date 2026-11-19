/*
 * Copyright 2026 Glow Launcher contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The Glow look of the settings screens: a deep violet night with pink and purple
 * light in the corners, and frosted glass cards on top of it. The launcher itself
 * uses the same palette (see GlowGlass), so settings no longer look like stock
 * Material.
 */
object GlowSettingsColors {
    val Base = Color(0xFF120B1E)
    val GlowPink = Color(0xFFFF4FB8)
    val GlowViolet = Color(0xFF7B3FF2)
    val GlowBlue = Color(0xFF4F6BFF)

    /** Frosted card fill and its hairline rim. */
    val Card = Color(0x1FE9D8FF)
    val CardPressed = Color(0x33E9D8FF)
    val CardRim = Color(0x26FFFFFF)

    val Accent = Color(0xFFE38BFF)
    val OnAccent = Color(0xFF2A0B3A)
    val Text = Color(0xFFF6F0FF)
    val TextSecondary = Color(0xFFC3B6DB)
}

/** One fixed dark palette: the glass needs a dark night behind it to read as glass. */
fun glowColorScheme(): ColorScheme = darkColorScheme(
    primary = GlowSettingsColors.Accent,
    onPrimary = GlowSettingsColors.OnAccent,
    primaryContainer = Color(0xFF5B2A86),
    onPrimaryContainer = Color(0xFFF6D9FF),
    inversePrimary = Color(0xFF8E3FC4),
    secondary = Color(0xFFFF8FC8),
    onSecondary = Color(0xFF3D0A26),
    secondaryContainer = Color(0xFF4A2462),
    onSecondaryContainer = Color(0xFFFFD8EC),
    tertiary = Color(0xFFA894FF),
    onTertiary = Color(0xFF1F1252),
    tertiaryContainer = Color(0xFF3A2A7A),
    onTertiaryContainer = Color(0xFFE4DDFF),
    background = GlowSettingsColors.Base,
    onBackground = GlowSettingsColors.Text,
    surface = GlowSettingsColors.Base,
    onSurface = GlowSettingsColors.Text,
    surfaceVariant = Color(0xFF2E2342),
    onSurfaceVariant = GlowSettingsColors.TextSecondary,
    surfaceTint = GlowSettingsColors.Accent,
    inverseSurface = Color(0xFFEDE4FA),
    inverseOnSurface = Color(0xFF231A30),
    outline = Color(0xFF75668F),
    outlineVariant = Color(0xFF3B3052),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A2D52),
    surfaceDim = Color(0xFF0E0918),
    surfaceContainerLowest = Color(0xFF0E0918),
    surfaceContainerLow = Color(0xFF1B1329),
    surfaceContainer = Color(0xFF221832),
    surfaceContainerHigh = Color(0xFF2B2040),
    surfaceContainerHighest = Color(0xFF34284C),
)

/** Night background with soft pink, violet and blue glows in the corners. */
fun Modifier.glowSettingsBackground(): Modifier = this
    .background(GlowSettingsColors.Base)
    .drawBehind {
        val w = size.width
        val h = size.height
        val r = maxOf(w, h)
        drawRect(
            Brush.radialGradient(
                listOf(GlowSettingsColors.GlowPink.copy(alpha = 0.32f), Color.Transparent),
                center = Offset(w * 1.05f, h * 0.02f),
                radius = r * 0.62f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(GlowSettingsColors.GlowViolet.copy(alpha = 0.34f), Color.Transparent),
                center = Offset(-w * 0.1f, h * 0.55f),
                radius = r * 0.6f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(GlowSettingsColors.GlowBlue.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(w * 0.9f, h * 1.02f),
                radius = r * 0.5f,
            ),
        )
    }

/** Hairline light rim of a glass card. */
fun Modifier.glowCardRim(shape: androidx.compose.ui.graphics.Shape): Modifier =
    border(androidx.compose.ui.unit.Dp.Hairline, GlowSettingsColors.CardRim, shape)
