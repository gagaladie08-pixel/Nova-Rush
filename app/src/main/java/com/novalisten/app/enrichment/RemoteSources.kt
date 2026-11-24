package com.novalisten.app.enrichment

import com.novalisten.app.BuildConfig
import com.novalisten.app.data.artistNameKey
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private const val MIN_ARTIST_CONTEXT_DURATION = 60_000L

/** Adapters never start themselves: explicit commands or the user-enabled current-track lyrics loader. */
class RemoteSources(val transport: RemoteTransport, private val credential: suspend (SecretSlot) -> String?) : LyricLookup {
    private suspend fun required(slot: SecretSlot): String = credential(slot)?.takeIf { it.isNotBlank() }
        ?: throw RemoteFailure("${slot.label} : non configuré. Ouvrez Réglages → API et clés.")
    override suspend fun availableLyricProviders(): List<Provider> = buildList {
        // No invented application email; an absent/unreadable contact skips only lrc.red.
        val contact = try { credential(SecretSlot.LRC_CONTACT) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { null }
        if (!contact.isNullOrBlank() && runCatching { SecretSlot.LRC_CONTACT.validate(contact) }.isSuccess) add(Provider.LRCRED)
        add(Provider.LRCLIB); add(Provider.LIRIQO)
    }
    override suspend fun lyrics(provider: Provider, input: MusicQuery): List<LyricCandidate> {
        val q = input.checked()
        return when (provider) {
            Provider.LRCLIB -> RemoteParsers.lrclib(transport.request(provider, "/api/search", mapOf("track_name" to q.title, "artist_name" to q.artist)))
            Provider.LRCRED -> {
                val contact = SecretSlot.LRC_CONTACT.validate(required(SecretSlot.LRC_CONTACT))
                val headers = mapOf("User-Agent" to "NovaListen/${BuildConfig.VERSION_NAME} (https://github.com/gagaladie08-pixel/Nova-Rush; $contact)")
                RemoteParsers.lrcRed(transport.request(provider, "/search.json", mapOf("q" to "${q.title} ${q.artist}"), headers))
            }
            Provider.LIRIQO -> RemoteParsers.liriqo(transport.request(provider, "/v1/lyrics", buildMap {
                put("title", q.title); put("artist", q.artist)
                if (q.album.isNotBlank()) put("album", q.album)
                if (q.durationMs > 0) put("duration", (q.durationMs / 1000).toString())
            }), q)
            Provider.BETTER -> {
                require(q.album.isNotBlank() && q.durationMs > 0) { "Better Lyrics : album et durée nécessaires pour rechercher correctement le cache." }
                RemoteParsers.better(transport.request(provider, "/getLyrics", mapOf("s" to q.title, "a" to q.artist, "al" to q.album, "d" to (q.durationMs / 1000).toString())), q)
            }
            Provider.TEXTYL -> RemoteParsers.textyl(transport.request(provider, "/api/lyrics", mapOf("q" to "${q.title} ${q.artist}")), q)
            Provider.OVH -> {
                fun segment(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
                RemoteParsers.ovh(transport.request(provider, "/v1/${segment(q.artist)}/${segment(q.title)}"), q)
            }
            else -> throw IllegalArgumentException("Source de paroles non prise en charge.")
        }
    }
    override suspend fun loadLyrics(candidate: LyricCandidate): LyricCandidate {
        if (candidate.text.isNotBlank()) return candidate
        val isrc = candidate.remoteIsrc ?: throw RemoteFailure("Aucun texte chargeable pour ce résultat.")
        require(isrc.matches(Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")))
        val contact = SecretSlot.LRC_CONTACT.validate(required(SecretSlot.LRC_CONTACT))
        val headers = mapOf("User-Agent" to "NovaListen/${BuildConfig.VERSION_NAME} (https://github.com/gagaladie08-pixel/Nova-Rush; $contact)")
        val detail = jsonObject(transport.request(Provider.LRCRED, "/s/$isrc.json", headers = headers))
        val files = detail.optJSONObject("files") ?: throw RemoteFailure("lrc.red : aucun format exploitable annoncé.")
        val lrc = files.str("lrc")
        val ttml = files.str("ttml")
        val (format, path) = if (ttml.isNotBlank()) "ttml" to "/s/$isrc.ttml" else if (lrc.isNotBlank()) "lrc" to "/s/$isrc.lrc" else throw RemoteFailure("lrc.red : ce résultat n’a pas de LRC ou TTML exploitable dans cette version.")
        // Never follow a URL supplied by a response with our identifying headers.
        val raw = safeText(transport.request(Provider.LRCRED, path, headers = headers))
        val result = if (format == "ttml") NativeLyrics.ttml(raw) else NativeLyricResult(raw, emptyList())
        if (result.text.isBlank()) throw RemoteFailure("Le texte chargé est vide.")
        return candidate.copy(text = result.text, nativeLines = result.lines, timing = LyricFormats.timing(result.text, result.lines), credit = candidate.credit.copy(details = "Format source : $format. Temps natifs conservés lorsqu’ils existent ; aucune synchronisation mot à mot inventée."))
    }
    private fun term(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    suspend fun recordings(input: MusicQuery): List<CatalogCandidate> {
        val q = input.checked()
        return RemoteParsers.recordings(transport.request(Provider.MUSICBRAINZ, "/ws/2/recording/", mapOf("query" to "recording:${term(q.title)} AND artist:${term(q.artist)}", "fmt" to "json", "limit" to "12")))
    }
    suspend fun releases(album: String, artist: String): List<CatalogCandidate> {
        MusicQuery(album, artist).checked()
        return RemoteParsers.releases(transport.request(Provider.MUSICBRAINZ, "/ws/2/release/", mapOf("query" to "release:${term(album)} AND artist:${term(artist)}", "fmt" to "json", "limit" to "12")))
    }
    suspend fun artists(name: String): List<ArtistCandidate> {
        require(name.isNotBlank() && name.length <= 300)
        return RemoteParsers.artists(transport.request(Provider.MUSICBRAINZ, "/ws/2/artist/", mapOf("query" to "artist:${term(name)}", "fmt" to "json", "limit" to "8")))
    }
    suspend fun itunes(input: MusicQuery, country: String): List<CatalogCandidate> {
        val q = input.checked()
        require(country.matches(Regex("[A-Z]{2}"))) { "Choisissez explicitement la boutique iTunes (code pays à deux lettres)." }
        return RemoteParsers.itunes(transport.request(Provider.ITUNES, "/search", mapOf("term" to "${q.title} ${q.artist}", "media" to "music", "entity" to "song", "limit" to "12", "country" to country)))
    }
    suspend fun lastFmTags(input: MusicQuery): List<String> {
        val q = input.checked()
        val root = jsonObject(transport.request(Provider.LASTFM, "/2.0/", mapOf("method" to "track.getTopTags", "track" to q.title, "artist" to q.artist, "format" to "json", "api_key" to required(SecretSlot.LASTFM))))
        return root.optJSONObject("toptags")?.objects("tag").orEmpty().take(15).map { it.str("name") }
    }
    suspend fun discogs(input: MusicQuery): List<CatalogCandidate> {
        val q = input.checked()
        val root = jsonObject(transport.request(Provider.DISCOGS, "/database/search", mapOf("track" to q.title, "artist" to q.artist, "type" to "release", "per_page" to "8", "page" to "1"), mapOf("Authorization" to "Discogs token=${required(SecretSlot.DISCOGS)}")))
        return root.objects("results").map { item ->
            val id = item.str("id")
            CatalogCandidate("discogs:$id", item.str("title"), q.artist, year = item.str("year"), credit = Credit("Data provided by Discogs.", "https://www.discogs.com/release/$id", details = "NovaListen n’est ni affilié ni approuvé par Discogs. Consultation de travail seulement ; pas de tags permanents ni d’images téléchargées."), details = listOf(item.str("country"), item.str("format"), item.str("catno")).joinToString(" · "))
        }
    }
    suspend fun genius(name: String): List<CatalogCandidate> {
        require(name.isNotBlank() && name.length <= 300)
        val root = jsonObject(transport.request(Provider.GENIUS, "/search", mapOf("q" to name), mapOf("Authorization" to "Bearer ${required(SecretSlot.GENIUS)}")))
        return root.optJSONObject("response")?.objects("hits").orEmpty().take(8).mapNotNull { hit -> hit.optJSONObject("result")?.let { item ->
            CatalogCandidate("genius:${item.str("id")}", item.str("title"), item.optJSONObject("primary_artist")?.str("name").orEmpty(), credit = Credit("Genius", item.str("url"), details = "Lien de référence seulement. Aucun scraping de paroles ou téléchargement de portrait."))
        } }
    }
    suspend fun biographies(name: String, language: String): List<Biography> {
        require(name.isNotBlank() && name.length <= 300)
        val provider = wikiProvider(language)
        return RemoteParsers.biography(transport.request(provider, "/w/api.php", mapOf("action" to "query", "generator" to "search", "gsrsearch" to name, "gsrnamespace" to "0", "gsrlimit" to "5", "prop" to "extracts|info|pageprops", "explaintext" to "1", "exintro" to "1", "inprop" to "url", "format" to "json", "formatversion" to "2")), language)
    }
    suspend fun fullBiography(bio: Biography): Biography {
        require(bio.id.matches(Regex("[0-9]+")))
        return RemoteParsers.biography(transport.request(wikiProvider(bio.language), "/w/api.php", mapOf("action" to "query", "pageids" to bio.id, "prop" to "extracts|info", "explaintext" to "1", "inprop" to "url", "format" to "json", "formatversion" to "2")), bio.language, true).firstOrNull() ?: throw RemoteFailure("Texte d’article indisponible.")
    }
    private fun wikiProvider(language: String): Provider = when (language) { "fr" -> Provider.WIKIPEDIA_FR; "en" -> Provider.WIKIPEDIA_EN; else -> throw IllegalArgumentException("Langue non prise en charge.") }
    suspend fun wikidata(name: String): List<ArtistCandidate> {
        require(name.isNotBlank() && name.length <= 300)
        return RemoteParsers.wikidataSearch(transport.request(Provider.WIKIDATA, "/w/api.php", mapOf("action" to "wbsearchentities", "search" to name, "language" to "fr", "uselang" to "fr", "type" to "item", "limit" to "5", "format" to "json")))
    }
    /** A unique name result verifies a catalogue entity, not its attribution to a local file. */
    suspend fun artistForCurrentRecording(request: ArtistRequest): String? {
        val local = request.context ?: return null
        if (local.title.isBlank() || local.title.length > 300 || local.album.length > 300 || local.durationMs < MIN_ARTIST_CONTEXT_DURATION || listOf(local.title, local.album, request.name).any { value -> value.any(Char::isISOControl) }) return null
        val native = com.novalisten.app.data.validMusicId(local.recordingId)
        val body = if (native) transport.request(Provider.MUSICBRAINZ, "/ws/2/recording/${RemoteSafety.mbid(local.recordingId)}", mapOf("inc" to "artist-credits+releases", "fmt" to "json"))
            else {
                if (local.album.isBlank() || local.album.artistNameKey() in setOf("album inconnu", "unknown album")) return null
                transport.request(Provider.MUSICBRAINZ, "/ws/2/recording/", mapOf("query" to "recording:${term(local.title)} AND artist:${term(request.name)} AND release:${term(local.album)}", "fmt" to "json", "limit" to "12"))
            }
        val root = jsonObject(body)
        val recordings = if (native) {
            if (!root.str("id").equals(local.recordingId, true)) return null
            listOf(root)
        } else {
            val results = root.objects("recordings")
            // An incomplete result page cannot rule out another artist with the same name.
            if (root.optInt("count", -1) !in 0..12 || root.optInt("count", -1) != results.size) return null
            results.take(12)
        }
        val ids = recordings.filter { recording ->
            recording.str("title").artistNameKey() == local.title.artistNameKey() && recording.optLong("length", 0) > 0 &&
                kotlin.math.abs(recording.optLong("length", 0) - local.durationMs) <= 2000 &&
                (native || recording.objects("releases").any { it.str("title").artistNameKey() == local.album.artistNameKey() })
        }.flatMap { recording -> recording.objects("artist-credit").mapNotNull { credit ->
            val artist = credit.optJSONObject("artist") ?: return@mapNotNull null
            artist.str("id").takeIf { com.novalisten.app.data.validMusicId(it) &&
                listOf(credit.str("name"), artist.str("name")).any { name -> name.artistNameKey() == request.name.artistNameKey() } }
        } }.map { it.lowercase(java.util.Locale.ROOT) }.distinct()
        return ids.singleOrNull()
    }
    /** Resolve an unstructured collaboration from the recording's complete native credit, not a name hit. */
    suspend fun collaborationCredits(request: ArtistRequest): List<com.novalisten.app.data.ArtistCredit> {
        val local = request.context ?: return emptyList()
        val candidates = com.novalisten.app.data.ArtistCredits.candidates(request.name).map { it.artistNameKey() }.toSet()
        if (candidates.size !in 2..16 || local.title.isBlank() || local.durationMs < MIN_ARTIST_CONTEXT_DURATION ||
            listOf(local.title, local.album, request.name).any { it.length > 300 || it.any(Char::isISOControl) }) return emptyList()
        val native = com.novalisten.app.data.validMusicId(local.recordingId)
        if (!native && (local.album.isBlank() || local.album.artistNameKey() in setOf("album inconnu", "unknown album"))) return emptyList()
        val root = jsonObject(if (native) transport.request(Provider.MUSICBRAINZ,
            "/ws/2/recording/${RemoteSafety.mbid(local.recordingId)}", mapOf("inc" to "artist-credits+releases", "fmt" to "json"))
            else transport.request(Provider.MUSICBRAINZ, "/ws/2/recording/", mapOf(
                "query" to "recording:${term(local.title)} AND release:${term(local.album)}", "fmt" to "json", "limit" to "12")))
        val found = if (native) {
            if (!root.str("id").equals(local.recordingId, true)) return emptyList()
            listOf(root)
        } else root.objects("recordings").also { if (root.optInt("count", -1) != it.size || it.size > 12) return emptyList() }
        val matches = found.filter { recording -> recording.str("title").artistNameKey() == local.title.artistNameKey() &&
            recording.optLong("length", 0) > 0 && kotlin.math.abs(recording.optLong("length", 0) - local.durationMs) <= 2000 &&
            (native || recording.objects("releases").any { it.str("title").artistNameKey() == local.album.artistNameKey() })
        }.mapNotNull { recording ->
            val rawCredits = recording.objects("artist-credit")
            val credits = rawCredits.mapNotNull { item -> item.optJSONObject("artist")?.let { artist ->
                val name = listOf(item.str("name"), artist.str("name")).firstOrNull { it.artistNameKey() in candidates }
                artist.str("id").takeIf { name != null && com.novalisten.app.data.validMusicId(it) }
                    ?.let { com.novalisten.app.data.ArtistCredit(name!!, it.lowercase(java.util.Locale.ROOT)) }
            } }
            credits.takeIf { it.size == rawCredits.size && it.size == candidates.size && it.map { c -> c.name.artistNameKey() }.toSet() == candidates }
        }.distinctBy { it.map { credit -> credit.id }.sorted() }
        return matches.singleOrNull().orEmpty()
    }
    suspend fun artistWikidata(mbid: String): String? {
        val root = jsonObject(transport.request(Provider.MUSICBRAINZ, "/ws/2/artist/${RemoteSafety.mbid(mbid)}", mapOf("inc" to "url-rels", "fmt" to "json")))
        if (!root.str("id").equals(mbid, true) || root.str("type") !in setOf("Person", "Group", "Orchestra", "Choir", "Character", "Other")) return null
        return root.objects("relations").filter { it.str("type") == "wikidata" }.mapNotNull { relation ->
            val uri = runCatching { java.net.URI(relation.optJSONObject("url")?.str("resource").orEmpty()) }.getOrNull()
            uri?.takeIf { it.scheme in setOf("http", "https") && it.host in setOf("www.wikidata.org", "wikidata.org") && it.rawUserInfo == null }
                ?.path?.removePrefix("/wiki/")?.takeIf { it.matches(Regex("Q[1-9][0-9]*")) }
        }.distinct().singleOrNull()
    }
    suspend fun verifiedArtistEntity(qid: String, mbid: String): VerifiedArtistEntity? {
        val root = jsonObject(transport.request(Provider.WIKIDATA, "/w/api.php", mapOf("action" to "wbgetentities", "ids" to RemoteSafety.qid(qid), "props" to "claims|sitelinks", "format" to "json")))
        val entity = root.optJSONObject("entities")?.optJSONObject(qid) ?: return null
        val claims = entity.optJSONObject("claims") ?: return null
        fun strings(property: String): List<String> = claims.objects(property).filter { it.str("rank") != "deprecated" }.mapNotNull {
            it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.str("value")?.takeIf(String::isNotBlank)
        }.distinct()
        if (strings("P434").singleOrNull()?.equals(mbid, true) != true) return null
        return VerifiedArtistEntity(qid, entity.optJSONObject("sitelinks")?.optJSONObject("frwiki")?.str("title")?.takeIf(String::isNotBlank), strings("P18").singleOrNull())
    }
    suspend fun biographyForEntity(title: String, qid: String): Biography? {
        require(title.length <= 300)
        val body = transport.request(Provider.WIKIPEDIA_FR, "/w/api.php", mapOf("action" to "query", "titles" to title, "redirects" to "1", "prop" to "extracts|info|pageprops", "explaintext" to "1", "exintro" to "1", "inprop" to "url", "format" to "json", "formatversion" to "2"))
        val pages = jsonObject(body).optJSONObject("query")?.objects("pages").orEmpty()
        if (pages.size != 1 || pages.single().optJSONObject("pageprops")?.str("wikibase_item") != qid) return null
        return RemoteParsers.biography(body, "fr").singleOrNull()
    }
    suspend fun wikidataDetails(id: String): ArtistCandidate = RemoteParsers.wikidataEntity(transport.request(Provider.WIKIDATA, "/w/api.php", mapOf("action" to "wbgetentities", "ids" to RemoteSafety.qid(id), "props" to "claims|labels|descriptions", "languages" to "fr|en", "format" to "json")), id)
    suspend fun commons(name: String, page: Int = 1, fileTitle: String? = null): List<ImageCandidate> {
        require(name.isNotBlank() && name.length <= 300 && page in 1..100)
        val params = mutableMapOf("action" to "query", "prop" to "imageinfo", "iiprop" to "url|extmetadata", "iiurlwidth" to "320", "format" to "json", "formatversion" to "2")
        if (fileTitle != null) { require(fileTitle.length <= 500); params["titles"] = "File:$fileTitle" }
        else params.putAll(mapOf("generator" to "search", "gsrsearch" to "$name filetype:bitmap", "gsrnamespace" to "6", "gsrlimit" to "6", "gsroffset" to ((page - 1) * 6).toString()))
        return RemoteParsers.commons(transport.request(Provider.COMMONS, "/w/api.php", params))
    }
    suspend fun openverse(name: String, page: Int = 1): List<ImageCandidate> {
        require(name.isNotBlank() && name.length <= 300 && page in 1..100)
        return RemoteParsers.openverse(transport.request(Provider.OPENVERSE, "/v1/images/", mapOf("q" to name, "page_size" to "6", "page" to page.toString(), "license" to "by,by-sa,cc0,pdm")))
    }
    suspend fun covers(id: String, group: Boolean = false): List<ImageCandidate> = RemoteParsers.covers(transport.request(Provider.CAA, "/${if (group) "release-group" else "release"}/${RemoteSafety.mbid(id)}/"), id, group)
    suspend fun fanart(id: String, artist: Boolean): List<ImageCandidate> {
        RemoteSafety.mbid(id)
        val project = credential(SecretSlot.FANART)
        val personal = credential(SecretSlot.FANART_PERSONAL)
        if (project.isNullOrBlank() && personal.isNullOrBlank()) throw RemoteFailure("fanart.tv : aucune clé configurée.")
        val headers = buildMap { if (!project.isNullOrBlank()) put("api-key", project); if (!personal.isNullOrBlank()) put("client-key", personal) }
        return RemoteParsers.fanart(transport.request(Provider.FANART, "/v3.2/music/${if (artist) "" else "albums/"}$id", headers = headers), id, artist)
    }
    suspend fun azure(plan: TranslationPlan, language: String): String {
        require(language in setOf("en", "ko", "es")) { "Choisissez la langue source." }
        val headers = buildMap {
            put("Ocp-Apim-Subscription-Key", required(SecretSlot.AZURE))
            credential(SecretSlot.AZURE_REGION)?.takeIf { it.isNotBlank() }?.let { put("Ocp-Apim-Subscription-Region", SecretSlot.AZURE_REGION.validate(it)) }
        }
        val translated = mutableMapOf<String, String>()
        plan.contents.distinct().chunked(50).forEach { batch ->
            val payload = JSONArray().apply { batch.forEach { put(JSONObject().put("Text", it)) } }.toString()
            val response = JSONArray(transport.request(Provider.AZURE, "/translate", mapOf("api-version" to "3.0", "from" to language, "to" to "fr", "textType" to "plain"), headers, payload))
            if (response.length() != batch.size) throw RemoteFailure("Azure : traduction incomplète, rien n’a été appliqué.")
            batch.forEachIndexed { index, text ->
                val result = response.getJSONObject(index).objects("translations").firstOrNull { it.str("to") == "fr" }?.str("text")
                if (result.isNullOrBlank()) throw RemoteFailure("Azure : segment manquant, rien n’a été appliqué.")
                translated[text] = result
            }
        }
        return plan.render(translated)
    }
}
