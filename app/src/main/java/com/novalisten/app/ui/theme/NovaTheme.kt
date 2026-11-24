package com.novalisten.app.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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

/** Niveaux de forme réellement distincts : cartes, boutons, panneaux. */
data class UniverseShapes(val card: CornerBasedShape, val button: CornerBasedShape, val panel: CornerBasedShape)

fun universeShape(spec: ThemeSpec): CornerBasedShape = cardShape(spec)
fun cardShape(spec: ThemeSpec): CornerBasedShape =
    if (spec.radius == null) CutCornerShape(percent = spec.cut.coerceAtLeast(8))
    else RoundedCornerShape(spec.radius.dp)
fun buttonShape(spec: ThemeSpec): CornerBasedShape =
    if (spec.radius == null) CutCornerShape(spec.buttonRadius.dp) else RoundedCornerShape(spec.buttonRadius.dp)
fun panelShape(spec: ThemeSpec): CornerBasedShape =
    if (spec.radius == null) CutCornerShape(percent = spec.cut.coerceAtLeast(8)) else RoundedCornerShape(spec.panelRadius.dp)

fun universeShapes(spec: ThemeSpec) = UniverseShapes(cardShape(spec), buttonShape(spec), panelShape(spec))

fun onColor(color: Color): Color = if (color.luminance() > 0.42f) Color(0xFF101014) else Color.White

/** Échelles typographiques adaptées au dessin de la police (les condensées paraissent plus petites). */
private fun titleSizes(scale: Float) = listOf(
    30f * scale, 28f * scale, 23f * scale, 21f * scale, 19f * scale,
)

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
            val base = themeCatalog.firstOrNull { it.id == json.optString("base", "cyber") } ?: themeCatalog.first()
            // Le point de départ est explicite : plus d'héritage silencieux de Cyber Nova.
            base.copy(
                id = "custom", name = "Mon univers", palette = colors,
                titleFont = json.optString("titleFont", base.titleFont).also { require(it in fontResources) },
                bodyFont = json.optString("bodyFont", base.bodyFont).also { require(it in fontResources) },
                radius = json.optInt("radius", base.cardRadius).coerceIn(0, 32).takeUnless { json.optBoolean("cut", false) },
                cut = if (json.optBoolean("cut", false)) json.optInt("cutSize", 12).coerceIn(4, 30) else 0,
                icons = runCatching { IconStyle.valueOf(json.optString("icons", base.icons.name)) }.getOrDefault(base.icons),
                glyphs = runCatching { GlyphFamily.valueOf(json.optString("glyphs", base.glyphs.name)) }.getOrDefault(base.glyphs),
                effect = json.optString("effect", base.effect),
                transitionMs = if (json.isNull("duration")) base.transitionMs else json.optInt("duration", 280).coerceIn(100, 700),
                curveStyle = base.curveStyle,
                curveWidth = json.optDouble("curveWidth", base.curveWidth.toDouble()).toFloat().coerceIn(1f, 6f),
                curveHalo = json.optDouble("curveHalo", base.curveHalo.toDouble()).toFloat().coerceIn(0f, 20f),
                titleScale = json.optDouble("titleScale", base.titleScale.toDouble()).toFloat().coerceIn(.9f, 1.5f),
                motion = base.motion.copy(curveReveal = runCatching { CurveReveal.valueOf(json.optString("reveal", base.motion.curveReveal.name)) }.getOrDefault(base.motion.curveReveal)),
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
            put("radius", value.cardRadius); put("cut", value.radius == null); put("cutSize", value.cut)
            put("icons", value.icons.name); put("glyphs", value.glyphs.name)
            put("base", spec.id.takeIf { id -> themeCatalog.any { it.id == id } } ?: "cyber")
            put("effect", value.effect); put("duration", value.transitionMs ?: JSONObject.NULL)
            put("curveStyle", value.curveStyle); put("curveWidth", value.curveWidth); put("curveHalo", value.curveHalo)
            put("titleScale", value.titleScale); put("reveal", value.motion.curveReveal.name)
        }
        preferences.edit().putString("custom", json.toString()).putString("selected", "custom").apply()
        _state.value = _state.value.copy(selected = "custom", custom = value)
    }
}

/**
 * Le thème transforme réellement l'application : couleurs (avec correction de lisibilité),
 * deux polices, trois niveaux de forme, composants Material, champs, curseurs, cases et onglets.
 */
@Composable
fun NovaTheme(spec: ThemeSpec, effects: Boolean = true, content: @Composable () -> Unit) {
    val title = FontFamily(Font(fontResources.getValue(spec.titleFont)))
    val body = FontFamily(Font(fontResources.getValue(spec.bodyFont)))
    val scheme = if (spec.light) lightColorScheme() else darkColorScheme()
    val surface = spec.surface
    val text = spec.text
    val secondaryText = spec.readableSecondaryText()
    val onSurfaceSecondary = spec.readableSecondaryText(onSurface = false)
    val colors = scheme.copy(
        primary = spec.primary, onPrimary = onColor(spec.primary), primaryContainer = surface, onPrimaryContainer = text,
        secondary = spec.secondary, onSecondary = onColor(spec.secondary), secondaryContainer = surface, onSecondaryContainer = text,
        tertiary = spec.accent, onTertiary = onColor(spec.accent), background = spec.background, onBackground = text,
        surface = surface, onSurface = text, surfaceVariant = surface, onSurfaceVariant = secondaryText,
        outline = secondaryText.copy(alpha = .85f), outlineVariant = spec.primary.copy(alpha = .30f),
        surfaceContainer = surface, surfaceContainerLow = surface, surfaceContainerHigh = surface,
        surfaceContainerHighest = surface, surfaceContainerLowest = spec.background,
        surfaceBright = surface, surfaceDim = spec.background,
        inverseSurface = text, inverseOnSurface = spec.background,
        error = if (spec.light) Color(0xFFB3261E) else Color(0xFFFFB4AB),
        onError = if (spec.light) Color.White else Color(0xFF3A0906),
        errorContainer = if (spec.light) Color(0xFFF9DEDC) else Color(0xFF3A0906),
        onErrorContainer = if (spec.light) Color(0xFF410E0B) else Color(0xFFFFDAD6),
        scrim = Color.Black,
    )
    val sizes = titleSizes(spec.titleScale)
    val tracking = spec.titleTracking.sp
    val typography = Typography(
        displayLarge = TextStyle(fontFamily = title, fontSize = (57 * spec.titleScale).sp, lineHeight = (64 * spec.titleScale).sp, letterSpacing = tracking),
        displayMedium = TextStyle(fontFamily = title, fontSize = (45 * spec.titleScale).sp, lineHeight = (52 * spec.titleScale).sp, letterSpacing = tracking),
        displaySmall = TextStyle(fontFamily = title, fontSize = sizes[0].sp, lineHeight = (sizes[0] * 1.26f).sp, letterSpacing = tracking),
        headlineLarge = TextStyle(fontFamily = title, fontSize = sizes[1].sp, lineHeight = (sizes[1] * 1.28f).sp, letterSpacing = tracking),
        headlineMedium = TextStyle(fontFamily = title, fontSize = sizes[2].sp, lineHeight = (sizes[2] * 1.34f).sp, letterSpacing = tracking),
        headlineSmall = TextStyle(fontFamily = title, fontSize = sizes[3].sp, lineHeight = (sizes[3] * 1.38f).sp, letterSpacing = tracking),
        titleLarge = TextStyle(fontFamily = title, fontSize = sizes[4].sp, lineHeight = (sizes[4] * 1.42f).sp, letterSpacing = tracking),
        titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 25.sp, letterSpacing = .1.sp),
        titleSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = .1.sp),
        bodyLarge = TextStyle(fontFamily = body, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = .15.sp),
        bodyMedium = TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = .15.sp),
        bodySmall = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = .2.sp),
        labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = .3.sp),
        labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = .4.sp),
        labelSmall = TextStyle(fontFamily = body, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = .5.sp),
    )
    val shapes = universeShapes(spec)
    val materialShapes = Shapes(
        extraSmall = shapes.button, small = shapes.button, medium = shapes.card, large = shapes.panel, extraLarge = shapes.panel,
    )
    CompositionLocalProvider(LocalUniverse provides spec, LocalEffects provides effects, LocalContentColor provides text) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = materialShapes) {
            // Styles appliqués à tout le contenu : texte, contenu, curseurs et cibles Material.
            CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                androidx.compose.foundation.LocalIndication provides androidx.compose.material3.ripple(color = spec.accent),
            ) { content() }
        }
    }
}

/** Puce de thème réutilisable : pastille de palette + nom, cohérente avec l'univers. */
@Composable
fun UniverseBadge(label: String, color: Color) {
    Text(label, color = color, style = MaterialTheme.typography.labelSmall)
}

/** Divider du thème, jamais un gris Material hors univers. */
@Composable
fun UniverseDivider(alpha: Float = .28f) {
    val t = LocalUniverse.current
    HorizontalDivider(color = t.primary.copy(alpha = alpha))
}

/** Couleur de surface légèrement teintée par l'accent, pour les fonds secondaires. */
fun ThemeSpec.tintedSurface(alpha: Float = .12f): Color = accent.copy(alpha = alpha).compositeOver(surface)
