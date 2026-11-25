package com.novalisten.app.data

import java.util.Locale

/**
 * Pourquoi deux cartes portent le même nom.
 *
 * Une carte d'artiste n'est pas identifiée par son nom mais par [ArtistCredit.id] : un
 * identifiant MusicBrainz quand le fichier en porte un, le nom normalisé sinon. Dès que deux
 * identifiants existent pour un même nom, le code refuse de fusionner — deux artistes homonymes
 * ne doivent jamais être confondus. Ce diagnostic montre la décision réelle, sans la deviner.
 */
data class ArtistKeyIssue(
    /** Nom affiché, tel que l'utilisateur le voit sur les deux cartes. */
    val name: String,
    /** Une ligne par carte : sa clé, son origine, et de quoi l'identifier. */
    val cards: List<Card>,
) {
    /**
     * @param key clé de regroupement réelle (« mbid:… » ou nom normalisé)
     * @param mbid identifiant MusicBrainz porté par les fichiers de cette carte, ou vide
     * @param origin d'où vient cette clé, en clair
     * @param tracks nombre de morceaux de cette carte
     * @param albums nombre de sorties de cette carte
     * @param sample deux titres pour reconnaître la carte sans ambiguïté
     */
    data class Card(
        val key: String,
        val mbid: String,
        val origin: String,
        val tracks: Int,
        val albums: Int,
        val sample: List<String>,
    )
}

/**
 * Regroupe les cartes qui partagent un nom affiché mais pas une clé.
 * @return uniquement les noms réellement dédoublés, triés par nom.
 */
fun splitArtistIdentities(
    tracks: List<Track>,
    choices: CatalogChoices = CatalogChoices(),
    albumsByArtist: Map<String, List<AlbumGroup>> = emptyMap(),
): List<ArtistKeyIssue> {
    val groups = artistGroups(tracks, choices)
    return groups
        .groupBy { it.name.artistNameKey() }
        .filterValues { it.size > 1 }
        .map { (_, cards) ->
            ArtistKeyIssue(
                name = cards.first().name,
                cards = cards.sortedByDescending { it.tracks.size }.map { group ->
                    val mbid = group.mbid.lowercase(Locale.ROOT)
                    ArtistKeyIssue.Card(
                        key = group.id,
                        mbid = mbid,
                        origin = when {
                            mbid.isNotBlank() -> "identifiant MusicBrainz"
                            group.id.startsWith("mbid:") -> "identifiant MusicBrainz (casse différente)"
                            else -> "nom seul, aucun identifiant"
                        },
                        tracks = group.tracks.size,
                        albums = albumsByArtist[group.id].orEmpty().size,
                        sample = group.tracks.take(2).map { it.title },
                    )
                },
            )
        }
        .sortedBy { it.name.normalized() }
}

import java.util.Locale
