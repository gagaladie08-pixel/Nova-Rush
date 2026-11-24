package com.novalisten.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.toColorInt
import com.novalisten.app.R

enum class IconStyle { HUD, ROUND, SHARP, ROYAL, BUBBLE, STAGE, SOLAR, METAL, PRISM, CRYSTAL, FRAME, GEOMETRIC, HALO }

/** Familles de glyphes réellement dessinées : chaque univers possède la sienne. */
enum class GlyphFamily { HUD, CHROME, SHARD, CROWN, BUBBLE, DRAPE, FANG, CLOUD, RAY, RIVET, PRIDE, GEM, POLAROID, DIAMOND, WING }

enum class CurveReveal { SWEEP, BLINK, HARSH, GLOW, STARS, SPOTLIGHT, DRIP, FADE, ORBIT, JITTER, RIBBON, SPARKLE, FLASH, DIAGONAL, ALTERNATE }

data class UniverseMotion(
    val curveReveal: CurveReveal,
    val breathe: Boolean = true,
    val decoratedArtwork: Boolean = true,
    val particleDensity: Float = 1f,
)

data class ThemeSpec(
    val id: String,
    val name: String,
    val palette: List<String>,
    val titleFont: String,
    val bodyFont: String,
    val radius: Int?,
    val icons: IconStyle,
    val effect: String,
    val transitionMs: Int?,
    val curveHalo: Float,
    val curveStyle: String = id,
    val curveWidth: Float = when (id) { "venom" -> 4f; "cloud" -> 1.5f; "revolution" -> 3f; else -> 2f },
    val glyphs: GlyphFamily = when (icons) {
        IconStyle.HUD, IconStyle.FRAME -> GlyphFamily.HUD
        IconStyle.ROUND -> GlyphFamily.CHROME
        IconStyle.SHARP -> GlyphFamily.SHARD
        IconStyle.ROYAL -> GlyphFamily.CROWN
        IconStyle.BUBBLE -> GlyphFamily.BUBBLE
        IconStyle.STAGE -> GlyphFamily.DRAPE
        IconStyle.SOLAR -> GlyphFamily.RAY
        IconStyle.METAL -> GlyphFamily.RIVET
        IconStyle.PRISM -> GlyphFamily.PRIDE
        IconStyle.CRYSTAL -> GlyphFamily.GEM
        IconStyle.GEOMETRIC -> GlyphFamily.DIAMOND
        IconStyle.HALO -> GlyphFamily.WING
    },
    val cut: Int = 0,
    val titleScale: Float = 1f,
    val titleTracking: Float = 0f,
    val motion: UniverseMotion = UniverseMotion(
        curveReveal = when (id) {
            "cyber" -> CurveReveal.SWEEP
            "disco" -> CurveReveal.BLINK
            "villain" -> CurveReveal.HARSH
            "queen" -> CurveReveal.GLOW
            "y2k" -> CurveReveal.STARS
            "velvet" -> CurveReveal.SPOTLIGHT
            "venom" -> CurveReveal.DRIP
            "cloud" -> CurveReveal.FADE
            "solara" -> CurveReveal.ORBIT
            "chaos" -> CurveReveal.JITTER
            "survivor" -> CurveReveal.RIBBON
            "rainbow" -> CurveReveal.SPARKLE
            "revolution" -> CurveReveal.FLASH
            "african" -> CurveReveal.DIAGONAL
            else -> CurveReveal.ALTERNATE
        },
        breath = id !in listOf("villain", "chaos", "venom"),
    ),
) {
    val primary get() = color(0)
    val secondary get() = color(1)
    val glow get() = color(2)
    val background get() = color(3)
    val surface get() = color(4)
    val text get() = color(5)
    val secondaryText get() = color(6)
    val accent get() = color(7)

    val light get() = background.luminance() > .5f

    /** Rayon des cartes, tel que fourni par le catalogue. */
    val cardRadius: Int get() = radius ?: cut.coerceAtLeast(8)

    /** Deuxième niveau : boutons et champs. Jamais le même rayon que les cartes. */
    val buttonRadius: Int get() = when {
        radius == null -> cut.coerceAtLeast(8) / 2
        radius == 0 -> 2
        else -> (radius * 3 / 5).coerceAtLeast(2)
    }

    /** Troisième niveau : panneaux, feuilles, dialogues. */
    val panelRadius: Int get() = when {
        radius == null -> cut.coerceAtLeast(8)
        radius == 0 -> 0
        else -> (radius * 5 / 4).coerceAtLeast(4)
    }

    /** Le texte secondaire ne doit jamais passer sous 3:1 : la correction est locale. */
    fun readableSecondaryText(onSurface: Boolean = true): Color {
        val background = if (onSurface) surface else this.background
        return accessibleText(secondaryText, background, ratio = 3.2f)
    }

    /** Couleur de texte lisible sur un aplat donné, sans changer la palette. */
    fun on(color: Color): Color = if (color.luminance() > .42f) Color(0xFF101014) else Color.White

    private fun color(index: Int) = Color(palette[index].toColorInt())
}

/**
 * Rapproche `candidate` de `text` jusqu'à atteindre le ratio demandé sur `background`.
 * La teinte (lumière relative) est conservée : aucune couleur du catalogue n'est remplacée.
 */
fun accessibleText(candidate: Color, background: Color, ratio: Float = 3.2f, toward: Color? = null): Color {
    if (composeContrast(candidate, background) >= ratio) return candidate
    val target = toward ?: if (background.luminance() > .5f) Color(0xFF101014) else Color.White
    var low = 0f
    var high = 1f
    repeat(18) {
        val middle = (low + high) / 2
        val mixed = androidx.compose.ui.graphics.lerp(candidate, target, middle)
        if (composeContrast(mixed, background) >= ratio) high = middle else low = middle
    }
    return androidx.compose.ui.graphics.lerp(candidate, target, high)
}

fun composeContrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + .05f) / (minOf(la, lb) + .05f)
}

// Palette order is exactly P/S/G/F/Surf/T/T2/A from conception fiche 21.
val themeCatalog = listOf(
    ThemeSpec("cyber", "Cyber Nova", listOf("#FF006E", "#00B4FF", "#BD00FF", "#050510", "#0D0D2B", "#F0F0FF", "#A0A0C0", "#00FFF0"), "orbitron", "rajdhani", 6, IconStyle.HUD, "cyber", 280, 8f,
        glyphs = GlyphFamily.HUD, titleScale = 1.02f, titleTracking = .04f),
    ThemeSpec("disco", "Neon Disco", listOf("#9B00FF", "#FFD700", "#FF69B4", "#0A0010", "#1A0030", "#E8E8FF", "#B0A0C0", "#C0C0C0"), "audiowide", "poppins", 24, IconStyle.ROUND, "disco", 300, 6f,
        glyphs = GlyphFamily.CHROME, titleScale = 1f, titleTracking = .01f),
    ThemeSpec("villain", "Villain Era", listOf("#CC0000", "#4A4A4A", "#8B0000", "#080808", "#111111", "#EEEEEE", "#888888", "#FF0000"), "bebasneue", "inter", 0, IconStyle.SHARP, "villain", 150, 1f,
        glyphs = GlyphFamily.SHARD, titleScale = 1.42f, titleTracking = .06f),
    ThemeSpec("queen", "Slay Queen", listOf("#FFD700", "#FF1493", "#FFA500", "#0A0A0A", "#1A1500", "#FFFFFF", "#D4AF37", "#B76E79"), "playfairdisplay", "montserrat", 18, IconStyle.ROYAL, "queen", 350, 12f,
        glyphs = GlyphFamily.CROWN, titleTracking = 0f),
    ThemeSpec("y2k", "Pink Y2K", listOf("#FF69B4", "#87CEEB", "#FF1493", "#FFF0F5", "#FFE4E1", "#8B0045", "#C06080", "#DA70D6"), "baloo2", "fredoka", 28, IconStyle.BUBBLE, "y2k", 320, 3f,
        glyphs = GlyphFamily.BUBBLE, titleScale = 1.04f, titleTracking = .01f),
    ThemeSpec("velvet", "Velvet Stage", listOf("#8B0000", "#722F37", "#CFB53B", "#0C0008", "#1A0010", "#FFF8DC", "#D4AF37", "#FFD700"), "abrilfatface", "poppins", 12, IconStyle.STAGE, "velvet", 500, 14f,
        glyphs = GlyphFamily.DRAPE, titleScale = 1.06f, titleTracking = .02f),
    ThemeSpec("venom", "Pink Venom", listOf("#FF0080", "#FF0030", "#FF69B4", "#000000", "#0D0008", "#FFFFFF", "#FFB6C1", "#FF007F"), "anton", "inter", null, IconStyle.SHARP, "venom", 150, 2f,
        glyphs = GlyphFamily.FANG, cut = 14, titleScale = 1.3f, titleTracking = .03f),
    ThemeSpec("cloud", "Cloud Nine", listOf("#FFB6C1", "#E6E6FA", "#FFC0CB", "#FAFAFA", "#FFF5F7", "#555555", "#9090A0", "#C8A2C8"), "comfortaa", "quicksand", 26, IconStyle.BUBBLE, "cloud", 450, 16f,
        glyphs = GlyphFamily.CLOUD, titleTracking = .01f),
    ThemeSpec("solara", "Solara", listOf("#FF6B35", "#C4622D", "#FFA500", "#1A0A00", "#2D1200", "#FFF3E0", "#D4A056", "#FFD700"), "dmserifdisplay", "poppins", 20, IconStyle.SOLAR, "solara", 300, 10f,
        glyphs = GlyphFamily.RAY, titleScale = 1.08f),
    ThemeSpec("chaos", "Chaos Born", listOf("#C0C0C0", "#1A1A1A", "#E8E8E8", "#050505", "#111111", "#F0F0F0", "#909090", "#DFDFDF"), "syne", "unicaone", 2, IconStyle.METAL, "chaos", null, 3f,
        glyphs = GlyphFamily.RIVET, titleScale = 1.12f, titleTracking = .03f, motion = UniverseMotion(CurveReveal.JITTER, breath = false)),
    ThemeSpec("survivor", "Survivor", listOf("#F77FB4", "#5BCEFA", "#9B4F96", "#080808", "#121018", "#FFFFFF", "#FFB7D5", "#26CEAA"), "righteous", "nunito", 16, IconStyle.PRISM, "survivor", null, 6f,
        glyphs = GlyphFamily.PRIDE, titleScale = 1.02f, titleTracking = .02f, motion = UniverseMotion(CurveReveal.RIBBON)),
    ThemeSpec("rainbow", "Rainbow Pop", listOf("#FF4DA6", "#4D79FF", "#9B4DFF", "#0A0010", "#100020", "#FFFFFF", "#FFB3D9", "#FFD700"), "bungee", "baloo2", 22, IconStyle.CRYSTAL, "rainbow", 250, 12f,
        glyphs = GlyphFamily.GEM, titleScale = 1.12f, titleTracking = .01f, motion = UniverseMotion(CurveReveal.SPARKLE)),
    ThemeSpec("revolution", "Pop Revolution", listOf("#4A90D9", "#FF6B9D", "#00BFFF", "#050A1A", "#0A1428", "#FFFFFF", "#A0C4FF", "#F0F8FF"), "bowlbyonesc", "inter", 10, IconStyle.FRAME, "revolution", 180, 1f,
        glyphs = GlyphFamily.POLAROID, motion = UniverseMotion(CurveReveal.FLASH)),
    ThemeSpec("african", "African Confessions", listOf("#C4622D", "#2D5A27", "#D4A017", "#120800", "#1E0E00", "#FFF3E0", "#C4922D", "#FFB347"), "fraunces", "ubuntu", 8, IconStyle.GEOMETRIC, "african", 320, 5f,
        glyphs = GlyphFamily.DIAMOND, titleScale = 1.04f, motion = UniverseMotion(CurveReveal.DIAGONAL)),
    ThemeSpec("angel", "Bad Angel", listOf("#CC0033", "#FFFFFF", "#FF3366", "#080808", "#141414", "#FFFFFF", "#AAAAAA", "#FF0044"), "cinzel", "cormorant", 14, IconStyle.HALO, "angel", 300, 8f,
        glyphs = GlyphFamily.WING, titleScale = 1.06f, titleTracking = .05f, motion = UniverseMotion(CurveReveal.ALTERNATE)),
)

val fontResources = linkedMapOf(
    "orbitron" to R.font.orbitron,
    "rajdhani" to R.font.rajdhani,
    "audiowide" to R.font.audiowide,
    "poppins" to R.font.poppins,
    "bebasneue" to R.font.bebasneue,
    "inter" to R.font.inter,
    "playfairdisplay" to R.font.playfairdisplay,
    "montserrat" to R.font.montserrat,
    "baloo2" to R.font.baloo2,
    "fredoka" to R.font.fredoka,
    "abrilfatface" to R.font.abrilfatface,
    "anton" to R.font.anton,
    "comfortaa" to R.font.comfortaa,
    "quicksand" to R.font.quicksand,
    "dmserifdisplay" to R.font.dmserifdisplay,
    "syne" to R.font.syne,
    "unicaone" to R.font.unicaone,
    "righteous" to R.font.righteous,
    "nunito" to R.font.nunito,
    "bungee" to R.font.bungee,
    "bowlbyonesc" to R.font.bowlbyonesc,
    "fraunces" to R.font.fraunces,
    "ubuntu" to R.font.ubuntu,
    "cinzel" to R.font.cinzel,
    "cormorant" to R.font.cormorant
)
val fontLabels = mapOf(
    "orbitron" to "Orbitron", "rajdhani" to "Rajdhani", "audiowide" to "Audiowide", "poppins" to "Poppins",
    "bebasneue" to "Bebas Neue", "inter" to "Inter", "playfairdisplay" to "Playfair Display", "montserrat" to "Montserrat",
    "baloo2" to "Baloo 2", "fredoka" to "Fredoka", "abrilfatface" to "Abril Fatface", "anton" to "Anton",
    "comfortaa" to "Comfortaa", "quicksand" to "Quicksand", "dmserifdisplay" to "DM Serif Display", "syne" to "Syne",
    "unicaone" to "Unica One", "righteous" to "Righteous", "nunito" to "Nunito", "bungee" to "Bungee",
    "bowlbyonesc" to "Bowlby One SC", "fraunces" to "Fraunces", "ubuntu" to "Ubuntu", "cinzel" to "Cinzel", "cormorant" to "Cormorant",
)
