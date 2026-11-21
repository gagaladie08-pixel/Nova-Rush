package com.novalisten.app.enrichment

import com.novalisten.app.data.artistNameKey
import com.novalisten.app.data.validMusicId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** No first-result/name-only assignment. MusicBrainz → Wikidata backlink → exact FR page / P18. */
class AutomaticArtistLookup(private val remote: RemoteSources) : ArtistLookup {
    override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult {
        if (request.name.isBlank() || request.name.length > 300 || request.name.artistNameKey() in setOf("artiste inconnu", "unknown artist", "various artists", "artistes divers"))
            return ArtistResult(note = "Artiste non identifié : utilisez la correction locale des crédits.")
        val mbid = if (validMusicId(request.mbid)) request.mbid else {
            val matches = remote.artists(request.name).filter { it.name.artistNameKey() == request.name.artistNameKey() && validMusicId(it.mbid.orEmpty()) }.distinctBy { it.mbid }
            if (matches.size != 1) return ArtistResult(note = "Identité absente ou homonymes : choisissez une association MusicBrainz depuis Photos en ligne.")
            matches.single().mbid!!
        }
        val linked = remote.artistWikidata(mbid) ?: return ArtistResult(note = "Pas de lien d’identité Wikidata vérifiable. Aucun portrait choisi sur le seul nom.")
        val entity = remote.verifiedArtistEntity(linked, mbid)
            ?: return ArtistResult(note = "Les identifiants d’artiste ne concordent pas ; choix manuel nécessaire.")
        var bio: Biography? = null
        var selected: ImageCandidate? = null
        var proposals = emptyList<ImageCandidate>()
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
                // Bounded first discovery, not a fallback to a random first portrait. No automatic pagination.
                proposals = coroutineScope {
                    val commons = async { safeImages { remote.commons(request.name) } }
                    val openverse = async { safeImages { remote.openverse(request.name) } }
                    (commons.await() + openverse.await()).distinctBy { imageIdentity(it.original) }.take(12)
                }
                notices += "Aucun portrait directement lié et admissible. Les propositions restent à choisir manuellement."
            } else notices += "Portrait Commons lié par Wikidata P18, identité croisée MusicBrainz/Wikidata, licence ouverte et crédits conservés. La date n’est pas présumée récente."
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { notices += "Portrait : ${error.publicMessage()}" }
        if (bio != null) notices += "Introduction française conservée avec sa source ; pas de traduction automatique."
        return ArtistResult(bio, selected, proposals, notices.joinToString("\n"), identityVerified = true)
    }
    private suspend fun safeImages(block: suspend () -> List<ImageCandidate>): List<ImageCandidate> = try { block() }
        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() }
}

data class VerifiedArtistEntity(val qid: String, val frenchTitle: String?, val imageTitle: String?)
fun automaticPortraitAllowed(image: ImageCandidate, linkedTitle: String): Boolean = image.licenceKnown && !image.needsOriginCheck &&
    image.credit.provider == "Wikimedia Commons" && (image.credit.author.isNotBlank() || image.credit.licence.contains("publicdomain/")) && image.title.replace('_', ' ').trim() == linkedTitle.removePrefix("File:").replace('_', ' ').trim() &&
    !Regex("(?i)(?:^|[ _.-])(logo|signature|poster|albumcover|coverart|flag)(?:[ _.-]|$)").containsMatchIn(image.title) &&
    image.original.substringBefore('?').substringAfterLast('.').lowercase(java.util.Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp")
