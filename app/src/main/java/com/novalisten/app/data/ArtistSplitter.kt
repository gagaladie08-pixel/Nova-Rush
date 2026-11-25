package com.novalisten.app.data

/**
 * Règles de découpage des noms d'artistes, réglables depuis les Paramètres.
 *
 * Le code ne peut pas savoir que « Earth, Wind & Fire » est un groupe et que « Rihanna, Drake »
 * est un duo : c'est l'utilisateur qui le dit, par la liste des noms protégés et la liste des
 * séparateurs. Chaque découpage reste vérifiable et réversible.
 */
data class SplitRules(
    val protectedNames: List<String> = DEFAULT_PROTECTED,
    val separators: List<String> = DEFAULT_SEPARATORS,
) {
    /** Séparateurs actifs, du plus fort au plus faible. « & » est désactivé par défaut. */
    val active: List<String> get() = separators.map { it.trim() }.filter { it in AVAILABLE }

    fun isProtected(name: String): Boolean = protectedNames.any { it.artistNameKey() == name.artistNameKey() }

    companion object {
        /** Doivent valoir au plus 2 caractères. */
        val AVAILABLE = listOf(";", "feat.", "featuring", "ft.", "/", ",", "&")
        val DEFAULT_SEPARATORS = listOf(";", "feat.", "featuring", "ft.", "/", ",")
        val DEFAULT_PROTECTED = listOf(
            "AC/DC", "+/-", "Earth, Wind & Fire", "Tyler, the Creator", "and/or", "Simon & Garfunkel",
            "Kool & the Gang", "Florence & the Machine", "of Monsters and Men", "Angus & Julia Stone",
            "Belle & Sebastian", "Years & Years",
        )
        /** Jamais découpés, quoi qu'il arrive : ce sont des albums collectifs, pas des noms mal formés. */
        val NEVER_SPLIT = listOf("Artistes divers", "Various Artists", "Artiste inconnu", "Unknown Artist")
    }
}

object ArtistSplitter {
    // Un séparateur est du texte saisi par l'utilisateur : il doit être échappé, jamais interprété
    // comme une expression régulière (sinon « . » devient « n'importe quel caractère »).
    /** Découpe une chaîne selon les règles actives ; retourne la chaîne d'origine telle quelle si protégée. */
    fun split(raw: String, rules: SplitRules): List<String> {
        val value = CatalogText.display(raw).trim()
        if (value.isBlank()) return emptyList()
        if (rules.isProtected(value)) return listOf(value)
        if (SplitRules.NEVER_SPLIT.any { it.artistNameKey() == value.artistNameKey() }) return listOf(value)
        // Chaque séparateur garde l'ordre de la chaîne : on collecte les coupures puis on tranche.
        val cuts = mutableListOf<IntRange>()
        rules.active.forEach { separator ->
            val pattern = when (separator) {
                ";" -> Regex("\\s*;\\s*")
                "feat." -> Regex("(?i)\\s+feat\\.?\\s+")
                "featuring" -> Regex("(?i)\\s+featuring\\s+")
                "ft." -> Regex("(?i)\\s+ft\\.?\\s+")
                "/" -> Regex("\\s*/\\s*")
                "," -> Regex("\\s*,\\s*")
                "&" -> Regex("\\s+&\\s+")
                // Séparateur libre saisi par l'utilisateur : échappé, jamais interprété.
                else -> Regex("\\s*" + Regex.escape(separator) + "\\s*")
            }
            pattern.findAll(value).forEach { cuts += it.range }
        }
        if (cuts.isEmpty()) return listOf(value)
        val ordered = cuts.sortedBy { it.first }
        val parts = mutableListOf<String>()
        var cursor = 0
        ordered.forEach { range -> if (range.first >= cursor) { parts += value.substring(cursor, range.first); cursor = range.last + 1 } }
        parts += value.substring(cursor)
        val cleaned = parts.map { it.trim() }.filter { it.isNotBlank() }
        // Un morceau trop court signale une coupure abusive (« AC/DC » -> « AC » + « DC »).
        if (cleaned.size < 2 || cleaned.any { it.length < 2 }) return listOf(value)
        // Un nom protégé qui réapparaît après découpe annule aussi le découpage de son parent.
        if (cleaned.any { rules.isProtected(it) }) return listOf(value)
        return cleaned.distinctBy { it.artistNameKey() }
    }

    /** Vrai si ce nom serait réellement découpé, pour l'aperçu et l'audit des Paramètres. */
    fun wouldSplit(raw: String, rules: SplitRules): Boolean = split(raw, rules).size > 1
}
