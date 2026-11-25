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
    /**
     * Séparateurs réellement appliqués. Les séparateurs connus doivent être cochés, mais un
     * séparateur saisi librement dans les Paramètres doit agir : sinon « Ajouter » ne ferait rien.
     */
    val active: List<String> get() = separators.map { it.trim() }.filter { it.isNotEmpty() }

    // Un nom est comparé à toute la liste protégée pour chaque morceau du catalogue : mémoriser
    // les clés normalisées évite de recalculer la même normalisation des dizaines de milliers de fois.
    private val protectedKeys: Set<String> by lazy { protectedNames.map { it.artistNameKey() }.toSet() }

    fun isProtected(name: String): Boolean = name.artistNameKey() in protectedKeys

    companion object {
        /** Doivent valoir au plus 2 caractères. */
        val AVAILABLE = listOf(";", "feat.", "featuring", "ft.", "/", ",", "&")
        /**
         * Séparateurs actifs au départ. « & » reste volontairement absent : c'est celui qui
         * ressemble le plus à un vrai nom de groupe. Les noms sensibles sont, eux, dans
         * [DEFAULT_PROTECTED] — c'est la liste de l'utilisateur qui fait foi, pas le code.
         */
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
    /**
     * Expressions figées une fois pour toutes.
     *
     * Cette fonction est appelée pour chaque morceau, pour chaque artiste, à chaque recalcul du
     * catalogue. Construire un objet Regex à chaque appel représentait des dizaines de milliers
     * de constructions inutiles sur une grande bibliothèque — l'une des raisons de la lenteur
     * de l'onglet Artistes. Un séparateur libre reste construit à la demande, mais il est rare.
     */
    private val patterns = mapOf(
        ";" to Regex("\\s*;\\s*"),
        "feat." to Regex("(?i)\\s+feat\\.?\\s+"),
        "featuring" to Regex("(?i)\\s+featuring\\s+"),
        "ft." to Regex("(?i)\\s+ft\\.?\\s+"),
        "/" to Regex("\\s*/\\s*"),
        "," to Regex("\\s*,\\s*"),
        "&" to Regex("(?i)\\s+&\\s+"),
    )
    private val customPatterns = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    private fun patternFor(separator: String): Regex =
        patterns[separator] ?: customPatterns.getOrPut(separator) {
            // Séparateur libre saisi par l'utilisateur : échappé, jamais interprété.
            Regex("\\s*" + Regex.escape(separator) + "\\s*")
        }

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
            patternFor(separator).findAll(value).forEach { cuts += it.range }
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
