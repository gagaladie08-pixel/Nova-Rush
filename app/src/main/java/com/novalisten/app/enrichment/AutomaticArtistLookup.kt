package com.novalisten.app.enrichment

import com.novalisten.app.data.artistNameKey
import com.novalisten.app.data.validMusicId
import kotlinx.coroutines.CancellationException

/** No first-result/name-only assignment. MusicBrainz → Wikidata backlink → exact FR page / P18. */
class AutomaticArtistLookup(private val remote: RemoteSources) : ArtistLookup {
    private val identities = linkedMapOf<ArtistRequest, Triple<String, VerifiedArtistEntity, Long>>()
    override suspend fun progressively(request: ArtistRequest, biography: Boolean, portrait: Boolean, emit: suspend (ArtistResult) -> Unit) {
        if (biography) {
            val result = resolve(request, true, false)
            emit(result)
            if (!result.identityVerified) return
        }
        if (portrait) emit(resolve(request, false, true))
    }
    override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult {
        if (request.name.isBlank() || request.name.length > 300 || request.name.artistNameKey() in setOf("artiste inconnu", "unknown artist", "various artists", "artistes divers"))
            return ArtistResult(note = "Artiste non identifié : utilisez la correction locale des crédits.")
        val cached = identities[request]?.takeIf { it.third > System.currentTimeMillis() }
        val mbid = cached?.first ?: if (validMusicId(request.mbid)) request.mbid else remote.artistForCurrentRecording(request)
            ?: return ArtistResult(note = "Le nom seul ne prouve pas l’identité de l’artiste de ce fichier. Aucun contenu attribué sans identifiant local/choisi ou concordance titre, album, durée et crédits du morceau écouté. Vous pouvez choisir l’association dans Photos en ligne.")
        val linked = cached?.second?.qid ?: remote.artistWikidata(mbid) ?: return ArtistResult(note = "Pas de lien d’identité Wikidata vérifiable. Aucun portrait choisi sur le seul nom.")
        val entity = cached?.second ?: remote.verifiedArtistEntity(linked, mbid)
            ?: return ArtistResult(note = "Les identifiants d’artiste ne concordent pas ; choix manuel nécessaire.")
        identities[request] = Triple(mbid, entity, System.currentTimeMillis() + 600_000)
        while (identities.size > 128) identities.remove(identities.keys.first())
        var bio: Biography? = null
        var selected: ImageCandidate? = null
        val notices = mutableListOf<String>()
        if (biography) try {
            if (entity.frenchTitle.isNullOrBlank()) notices += "Aucun article français lié. Une recherche anglaise reste proposée manuellement, sans traduction automatique."
            else {
                bio = remote.biographyForEntity(entity.frenchTitle, entity.qid)
                if (bio == null) notices += "Introduction française non vérifiable ou absente."
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { notices += "Biographie : ${error.publicMessage()}" }
        if (portrait) try {
            entity.imageTitle?.let { title ->
                val images = remote.commons(request.name, fileTitle = title)
                selected = images.singleOrNull()?.takeIf { automaticPortraitAllowed(it, title) }
            }
            if (selected == null) {
                notices += "Aucun portrait directement lié et admissible. La cascade conditionnelle recherche un lot borné de propositions, sans choisir son premier résultat."
            } else notices += "Portrait Commons lié par Wikidata P18, identité croisée MusicBrainz/Wikidata, licence ouverte et crédits conservés. La date n’est pas présumée récente."
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { notices += "Portrait : ${error.publicMessage()}" }
        if (bio != null) notices += "Introduction française conservée avec sa source ; pas de traduction automatique."
        return ArtistResult(bio, selected, note = notices.joinToString("\n"), identityVerified = true, verifiedMbid = mbid)
    }
}

data class VerifiedArtistEntity(val qid: String, val frenchTitle: String?, val imageTitle: String?)
fun automaticPortraitAllowed(image: ImageCandidate, linkedTitle: String): Boolean = image.licenceKnown && !image.needsOriginCheck &&
    image.credit.provider == "Wikimedia Commons" && (image.credit.author.isNotBlank() || image.credit.licence.contains("publicdomain/")) && image.title.replace('_', ' ').trim() == linkedTitle.removePrefix("File:").replace('_', ' ').trim() &&
    !Regex("(?i)(?:^|[ _.-])(logo|signature|poster|albumcover|coverart|flag)(?:[ _.-]|$)").containsMatchIn(image.title) &&
    image.original.substringBefore('?').substringAfterLast('.').lowercase(java.util.Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp")
