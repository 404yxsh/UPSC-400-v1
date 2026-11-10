package com.yash.upsc400

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF0D0F14)
val DoneGreen = Color(0xFF5ED3B1)
val WarnAmber = Color(0xFFFFB86B)
val AlertRed = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CA8FF), onPrimary = Color(0xFF0B1230),
    secondary = DoneGreen, onSecondary = Color(0xFF00201A),
    tertiary = WarnAmber, error = AlertRed,
    background = Ink, onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF161922), onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF1F2330), onSurfaceVariant = Color(0xFFA9AEBF),
    outline = Color(0xFF3A3F52)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3556D6), onPrimary = Color.White,
    secondary = Color(0xFF0E8F73), onSecondary = Color.White,
    tertiary = Color(0xFFB36B00), error = Color(0xFFC62828),
    background = Color(0xFFF6F7FA), onBackground = Color(0xFF14161C),
    surface = Color.White, onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFEBEDF3), onSurfaceVariant = Color(0xFF555B6E),
    outline = Color(0xFFC3C7D4)
)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = bg, shape = shape, modifier = modifier.clip(shape).clickable { onClick() }) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

fun tierName(t: Int): String = when (t) { 0 -> "core"; 1 -> "standard"; else -> "extended" }

@Composable
fun tierColor(t: Int): Color = when (t) { 0 -> MaterialTheme.colorScheme.primary; 1 -> MaterialTheme.colorScheme.onSurfaceVariant; else -> MaterialTheme.colorScheme.tertiary }

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
