package com.novalisten.app.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

val LocalUniverse = staticCompositionLocalOf { themeCatalog.first() }
val LocalEffects = staticCompositionLocalOf { true }

data class ThemeSettings(val selected: String = "cyber", val custom: ThemeSpec? = null, val effects: Boolean = true) {
    val universe: ThemeSpec get() = if (selected == "custom" && custom != null) custom else themeCatalog.firstOrNull { it.id == selected } ?: themeCatalog.first()
}

class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("nova_appearance", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state = _state.asStateFlow()
    private fun read(): ThemeSettings {
        val custom = runCatching {
            val json = JSONObject(preferences.getString("custom", "")!!)
            val colors = json.getJSONArray("palette").let { a -> List(8) { a.getString(it).also { color -> require(Regex("#[0-9a-fA-F]{6}").matches(color)) } } }
            themeCatalog.first().copy(
                id = "custom", name = "Mon univers", palette = colors,
                titleFont = json.getString("titleFont").also { require(it in fontResources) },
                bodyFont = json.getString("bodyFont").also { require(it in fontResources) },
                radius = json.optInt("radius", 6).coerceIn(0, 32).takeUnless { json.optBoolean("cut", false) },
                icons = IconStyle.valueOf(json.getString("icons")), effect = json.getString("effect"),
                transitionMs = if (json.isNull("duration")) null else json.optInt("duration", 280).coerceIn(100, 700),
                curveStyle = json.optString("curveStyle", "cyber"), curveWidth = json.optDouble("curveWidth", 2.0).toFloat().coerceIn(1f, 6f),
                curveHalo = json.optDouble("curveHalo", 8.0).toFloat().coerceIn(0f, 20f),
            )
        }.getOrNull()
        return ThemeSettings(preferences.getString("selected", "cyber") ?: "cyber", custom, preferences.getBoolean("effects", true))
    }
    fun select(id: String) { preferences.edit().putString("selected", id).apply(); _state.value = _state.value.copy(selected = id) }
    fun effects(enabled: Boolean) { preferences.edit().putBoolean("effects", enabled).apply(); _state.value = _state.value.copy(effects = enabled) }
    fun saveCustom(spec: ThemeSpec) {
        val value = spec.copy(id = "custom", name = "Mon univers")
        val json = JSONObject().apply {
            put("palette", JSONArray(value.palette)); put("titleFont", value.titleFont); put("bodyFont", value.bodyFont)
            put("radius", value.radius ?: 0); put("cut", value.radius == null); put("icons", value.icons.name)
            put("effect", value.effect); put("duration", value.transitionMs ?: JSONObject.NULL)
            put("curveStyle", value.curveStyle); put("curveWidth", value.curveWidth); put("curveHalo", value.curveHalo)
        }
        preferences.edit().putString("custom", json.toString()).putString("selected", "custom").apply()
        _state.value = _state.value.copy(selected = "custom", custom = value)
    }
}

fun universeShape(spec: ThemeSpec): androidx.compose.foundation.shape.CornerBasedShape = spec.radius?.let { RoundedCornerShape(it.dp) } ?: CutCornerShape(percent = 12)
fun onColor(color: Color): Color = if (color.luminance() > 0.179f) Color.Black else Color.White

@Composable
fun NovaTheme(spec: ThemeSpec, effects: Boolean = true, content: @Composable () -> Unit) {
    val title = FontFamily(Font(fontResources.getValue(spec.titleFont)))
    val body = FontFamily(Font(fontResources.getValue(spec.bodyFont)))
    val scheme = if (spec.background.luminance() > .5f) lightColorScheme() else darkColorScheme()
    val colors = scheme.copy(
        primary = spec.primary, onPrimary = onColor(spec.primary), primaryContainer = spec.surface, onPrimaryContainer = spec.text,
        secondary = spec.secondary, onSecondary = onColor(spec.secondary), secondaryContainer = spec.surface, onSecondaryContainer = spec.text,
        tertiary = spec.accent, onTertiary = onColor(spec.accent), background = spec.background, onBackground = spec.text,
        surface = spec.surface, onSurface = spec.text, surfaceVariant = spec.surface, onSurfaceVariant = spec.text,
        outline = spec.secondaryText.copy(alpha = .6f), outlineVariant = spec.primary.copy(alpha = .2f),
        surfaceContainer = spec.surface, surfaceContainerLow = spec.surface, surfaceContainerHigh = spec.surface,
        surfaceContainerHighest = spec.surface, inverseSurface = spec.text, inverseOnSurface = spec.background,
    )
    val typography = Typography(
        displaySmall = TextStyle(fontFamily = title, fontSize = 30.sp, lineHeight = 38.sp),
        headlineLarge = TextStyle(fontFamily = title, fontSize = 28.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = title, fontSize = 23.sp, lineHeight = 31.sp),
        headlineSmall = TextStyle(fontFamily = title, fontSize = 21.sp, lineHeight = 29.sp),
        titleLarge = TextStyle(fontFamily = title, fontSize = 19.sp, lineHeight = 27.sp),
        titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 25.sp),
        titleSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 23.sp),
        bodyLarge = TextStyle(fontFamily = body, fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 23.sp),
        bodySmall = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
        labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
        labelSmall = TextStyle(fontFamily = body, fontSize = 12.sp, lineHeight = 17.sp),
    )
    val shape = universeShape(spec)
    CompositionLocalProvider(LocalUniverse provides spec, LocalEffects provides effects, androidx.compose.material3.LocalContentColor provides spec.text) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = Shapes(shape, shape, shape, shape, shape), content = content)
    }
}
