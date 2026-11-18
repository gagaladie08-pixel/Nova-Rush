package com.novalisten.app.enrichment

import com.novalisten.app.data.LrcParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

internal fun JSONObject.str(key: String): String = opt(key)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
internal fun JSONObject.objects(key: String): List<JSONObject> = optJSONArray(key).objects()
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
internal fun jsonObject(body: String): JSONObject {
    val root = JSONObject(body)
    if (root.has("error") && !root.isNull("error")) throw RemoteFailure("Le fournisseur a signalé une erreur ; ce n’est pas une absence de résultat.")
    return root
}
internal fun safeText(value: String, max: Int = 200_000): String {
    if (value.length > max) throw RemoteFailure("Texte trop volumineux. Aucun contenu tronqué n’a été sauvegardé.")
    return value.replace("\u0000", "")
}
internal fun cleanHtml(value: String): String = value.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
    .replace(Regex("(?i)<br\\s*/?>|</p>"), "\n").replace(Regex("<[^>]*>"), "")
    .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    .replace(Regex("&#(x[0-9a-fA-F]+|[0-9]+);")) { match ->
        runCatching { val n = match.groupValues[1]; val code = if (n.startsWith('x')) n.drop(1).toInt(16) else n.toInt(); String(Character.toChars(code)) }.getOrDefault("")
    }.trim()
internal fun lrcTime(ms: Long): String = "[%02d:%02d.%03d]".format(Locale.ROOT, ms / 60000, ms / 1000 % 60, ms % 1000)
internal fun asLrc(lines: List<Pair<Long, String>>): String = lines.joinToString("\n") { (ms, text) -> lrcTime(ms) + text }
internal fun secureCatalogueUrl(value: String): String {
    // CAA's documented legacy URLs use HTTP; upgrade only these known hosts, never downgrade TLS.
    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return value
    val host = uri.host.orEmpty()
    return if (uri.scheme == "http" && (host in setOf("coverartarchive.org", "archive.org", "musicbrainz.org") || host.endsWith(".archive.org"))) value.replaceFirst("http://", "https://") else value
}

object LyricFormats {
    /** Keeps real line starts. Word timings are not fabricated or advertised by the v0.2 line renderer. */
    fun ttmlToText(xml: String): String {
        safeText(xml)
        require(!xml.contains(Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE))) { "Déclarations XML externes interdites." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // The precheck above also protects Android parsers lacking these optional SAX features.
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val builder = factory.newDocumentBuilder().apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }
        val document = builder.parse(InputSource(StringReader(xml)))
        val paragraphs = document.getElementsByTagNameNS("*", "p")
        require(paragraphs.length <= 3000)
        val lines = (0 until paragraphs.length).map { i ->
            val node = paragraphs.item(i) as org.w3c.dom.Element
            clock(node.getAttribute("begin")) to node.textContent.trim()
        }.filter { it.second.isNotBlank() }
        if (lines.isEmpty()) return ""
        return if (lines.all { it.first != null }) asLrc(lines.map { it.first!! to it.second }) else lines.joinToString("\n") { it.second }
    }
    fun clock(value: String): Long? = runCatching {
        val raw = value.trim()
        val seconds = when {
            raw.endsWith("ms") -> return@runCatching raw.dropLast(2).toDouble().takeIf { it.isFinite() && it >= 0 }?.toLong()
            raw.endsWith('s') -> raw.dropLast(1).toDouble()
            ':' in raw -> raw.split(':').fold(0.0) { acc, part -> acc * 60 + part.toDouble() }
            else -> return@runCatching null
        }
        (seconds * 1000).takeIf { it.isFinite() && it in 0.0..86_400_000.0 }?.toLong()
    }.getOrNull()
    fun timing(text: String): String = if (LrcParser.parse(text).isNotEmpty()) "Synchro · Ligne" else "Texte non synchronisé"
}

object RemoteParsers {
    fun lrclib(body: String): List<LyricCandidate> {
        if (body.trim() == "null") return emptyList()
        return JSONArray(body).objects().take(20).mapNotNull { item ->
            val text = safeText(item.str("syncedLyrics").ifBlank { item.str("plainLyrics") })
            if (text.isBlank()) null else LyricCandidate("lrclib:${item.str("id")}", item.str("trackName"), item.str("artistName"), item.str("albumName"), (item.optDouble("duration", 0.0) * 1000).toLong(), Credit("LRCLIB", "https://lrclib.net/api/get/${item.str("id")}", details = "Droits des paroles à vérifier ; licence du serveur distincte."), text, timing = LyricFormats.timing(text))
        }
    }
    fun lrcRed(body: String): List<LyricCandidate> = jsonObject(body).objects("hits").take(12).mapNotNull { item ->
        val isrc = item.str("isrc")
        if (!isrc.matches(Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}"))) null else LyricCandidate("lrcred:$isrc", item.str("title"), item.str("artist"), item.str("album"), (item.optDouble("duration", 0.0) * 1000).toLong(), Credit("lrc.red", "https://lrc.red/s/$isrc", details = "Minutage à vérifier après chargement ; droits des paroles distincts."), remoteIsrc = isrc, timing = "Format à charger")
    }
    fun liriqo(body: String, query: MusicQuery): List<LyricCandidate> {
        val root = jsonObject(body)
        val metadata = root.optJSONObject("metadata") ?: JSONObject()
        val tracks = root.objects("tracks").ifEmpty { listOfNotNull(root.optJSONObject("primary")) }
        return tracks.take(16).mapIndexedNotNull { index, item ->
            val timed = item.objects("timed")
            val valid = timed.isNotEmpty() && timed.all { it.has("start") && it.optDouble("start", -1.0).let { n -> n.isFinite() && n in 0.0..86_400_000.0 } }
            val text = safeText(when {
                valid -> asLrc(timed.map { it.optDouble("start").toLong() to it.str("text") })
                item.str("lrc").isNotBlank() -> item.str("lrc")
                item.str("plain").isNotBlank() -> item.str("plain")
                item.str("ttml").isNotBlank() -> LyricFormats.ttmlToText(item.str("ttml"))
                else -> ""
            })
            val upstream = item.str("upstreamProvider").ifBlank { item.str("lyricsSource").ifBlank { item.str("provider") } }
            if (text.isBlank()) null else LyricCandidate("liriqo:$index:${item.str("provider")}", metadata.str("title").ifBlank { query.title }, metadata.str("artist").ifBlank { query.artist }, metadata.str("album").ifBlank { query.album }, (metadata.optDouble("duration", 0.0) * 1000).toLong(), Credit("LiriQo → $upstream", "https://github.com/AlFarrizi-Studio/LiriQo", details = "Source annoncée : ${item.str("syncLevel")}. Affichage ligne/texte dans NovaListen ; aucune traduction/romanisation automatique."), text, timing = LyricFormats.timing(text), upstream = upstream)
        }
    }
    fun better(body: String, query: MusicQuery): List<LyricCandidate> {
        if (body.trim() == "null") return emptyList()
        val root = jsonObject(body)
        val xml = root.str("ttml")
        if (xml.isBlank()) return emptyList()
        val text = LyricFormats.ttmlToText(xml)
        return if (text.isBlank()) emptyList() else listOf(LyricCandidate("better:${query.title}", query.title, query.artist, query.album, query.durationMs, Credit("Better Lyrics · cache public", "https://betterlyrics.org", details = "Origine du minutage non certifiée. TTML ramené à ses instants de ligne existants."), text, timing = LyricFormats.timing(text)))
    }
    fun textyl(body: String, query: MusicQuery): List<LyricCandidate> {
        if (body.trim() == "null") return emptyList()
        val entries = JSONArray(body).objects()
        if (entries.isEmpty()) return emptyList()
        if (entries.any { !it.has("seconds") || !it.optDouble("seconds", -1.0).let { n -> n.isFinite() && n in 0.0..86_400.0 } }) throw RemoteFailure("Textyl : horodatage de ligne invalide.")
        val text = safeText(asLrc(entries.map { (it.optDouble("seconds") * 1000).toLong() to it.str("lyrics") }))
        return listOf(LyricCandidate("textyl:${query.title}", query.title, query.artist, query.album, query.durationMs, Credit("Textyl", "https://textyl.co/", details = "Provenance amont non fournie ; correspondance et droits à vérifier."), text, timing = LyricFormats.timing(text)))
    }
    fun ovh(body: String, query: MusicQuery): List<LyricCandidate> {
        if (body.trim() == "null") return emptyList()
        val text = safeText(jsonObject(body).str("lyrics"))
        return if (text.isBlank()) emptyList() else listOf(LyricCandidate("ovh:${query.title}", query.title, query.artist, query.album, query.durationMs, Credit("lyrics.ovh", "https://lyrics.ovh/", details = "Peut réutiliser LRCLIB ; provenance amont non certifiée."), text))
    }
    private fun artistCredit(item: JSONObject): String = item.objects("artist-credit").joinToString("") { it.str("name").ifBlank { it.optJSONObject("artist")?.str("name").orEmpty() } + it.str("joinphrase") }
    fun recordings(body: String): List<CatalogCandidate> = jsonObject(body).objects("recordings").take(12).map { item ->
        val release = item.objects("releases").firstOrNull()
        CatalogCandidate(item.str("id"), item.str("title"), artistCredit(item), release?.str("title").orEmpty(), item.str("first-release-date"), item.optLong("length", 0), release?.str("id"), release?.optJSONObject("release-group")?.str("id"), Credit("MusicBrainz", "https://musicbrainz.org/recording/${item.str("id")}", "Données de base CC0"), "Enregistrement candidat, pas une preuve du fichier ni de son édition. ${item.str("disambiguation")}")
    }
    fun releases(body: String): List<CatalogCandidate> = jsonObject(body).objects("releases").take(12).map { item ->
        CatalogCandidate(item.str("id"), item.str("title"), artistCredit(item), item.str("title"), item.str("date"), releaseId = item.str("id"), groupId = item.optJSONObject("release-group")?.str("id"), credit = Credit("MusicBrainz", "https://musicbrainz.org/release/${item.str("id")}", "Données de base CC0"), details = listOf(item.str("country"), item.str("status"), item.str("disambiguation"), item.str("barcode")).filter { it.isNotBlank() }.joinToString(" · "))
    }
    fun artists(body: String): List<ArtistCandidate> = jsonObject(body).objects("artists").take(8).map { item ->
        ArtistCandidate(item.str("id"), item.str("name"), listOf(item.str("type"), item.str("country"), item.str("disambiguation")).filter { it.isNotBlank() }.joinToString(" · "), Credit("MusicBrainz", "https://musicbrainz.org/artist/${item.str("id")}", "Données de base CC0"), mbid = item.str("id"))
    }
    fun itunes(body: String): List<CatalogCandidate> = jsonObject(body).objects("results").filter { it.str("kind") == "song" }.take(12).map { item ->
        CatalogCandidate("itunes:${item.str("trackId")}", item.str("trackName"), item.str("artistName"), item.str("collectionName"), item.str("releaseDate").take(10), item.optLong("trackTimeMillis"), credit = Credit("iTunes Search", item.str("trackViewUrl"), details = "Référence de boutique, pas une licence de pochette hors ligne."), details = "Genre de boutique : ${item.str("primaryGenreName")} · piste ${item.str("trackNumber")}")
    }
    fun biography(body: String, language: String, full: Boolean = false): List<Biography> = jsonObject(body).optJSONObject("query")?.objects("pages").orEmpty().filter { !it.has("missing") && it.optJSONObject("pageprops")?.has("disambiguation") != true }.mapNotNull { item ->
        val extract = cleanHtml(item.str("extract"))
        if (extract.isBlank()) null else Biography(item.str("pageid"), item.str("title"), language, extract.take(30_000), Credit("Wikipédia ${language.uppercase(Locale.ROOT)}", item.str("fullurl").ifBlank { "https://$language.wikipedia.org/?curid=${item.str("pageid")}" }, "CC BY-SA 4.0 · auteurs via l’historique de l’article", details = "Révision ${item.str("lastrevid")}. ${if (full) "Texte d’article limité à 30 000 caractères" else "Introduction seulement, pas l’article complet"}. Signaler les adaptations."), full)
    }
    fun wikidataSearch(body: String): List<ArtistCandidate> = jsonObject(body).objects("search").map { item ->
        val id = RemoteSafety.qid(item.str("id"))
        ArtistCandidate(id, item.str("label"), item.str("description"), Credit("Wikidata", "https://www.wikidata.org/wiki/$id", "Données structurées CC0, pas les images"))
    }
    fun wikidataEntity(body: String, id: String): ArtistCandidate {
        val item = jsonObject(body).optJSONObject("entities")?.optJSONObject(id) ?: throw RemoteFailure("Entité Wikidata absente.")
        val claims = item.optJSONObject("claims") ?: JSONObject()
        fun claim(property: String): String? = claims.objects(property).firstOrNull()?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.str("value")?.takeIf { it.isNotBlank() }
        val label = item.optJSONObject("labels")?.optJSONObject("fr")?.str("value").orEmpty().ifBlank { item.optJSONObject("labels")?.optJSONObject("en")?.str("value").orEmpty() }
        val description = item.optJSONObject("descriptions")?.optJSONObject("fr")?.str("value").orEmpty()
        return ArtistCandidate(id, label.ifBlank { id }, description, Credit("Wikidata", "https://www.wikidata.org/wiki/$id", "CC0 · identité à vérifier"), claim("P18"), claim("P434"))
    }
    fun commons(body: String): List<ImageCandidate> = jsonObject(body).optJSONObject("query")?.objects("pages").orEmpty().mapNotNull { page ->
        val info = page.objects("imageinfo").firstOrNull() ?: return@mapNotNull null
        val meta = info.optJSONObject("extmetadata") ?: JSONObject()
        fun field(key: String) = cleanHtml(meta.optJSONObject(key)?.str("value").orEmpty())
        val original = info.str("url")
        val preview = info.str("thumburl").ifBlank { original }
        val licenceUrl = secureCatalogueUrl(field("LicenseUrl"))
        if (original.isBlank() || preview.isBlank()) null else ImageCandidate("commons:${page.str("pageid")}", page.str("title").removePrefix("File:"), preview, original,
            Credit("Wikimedia Commons", info.str("descriptionurl"), "${field("LicenseShortName")}\n$licenceUrl", field("Artist"), field("Credit")), RemoteSafety.openLicence(licenceUrl), date = field("DateTimeOriginal").takeIf { it.isNotBlank() }?.let { "Date indiquée par la source : $it (à vérifier)" } ?: "Date de prise de vue inconnue")
    }
    fun openverse(body: String): List<ImageCandidate> = jsonObject(body).objects("results").mapNotNull { item ->
        val licence = item.str("license_url")
        val original = item.str("url")
        if (original.isBlank() || item.str("thumbnail").isBlank()) null else ImageCandidate("openverse:${item.str("id")}", item.str("title"), item.str("thumbnail"), original, Credit("Openverse → ${item.str("source")}", item.str("foreign_landing_url"), "${item.str("license")} ${item.str("license_version")}\n$licence", item.str("creator"), "Vérifier la licence et le sujet sur la page d’origine ; Openverse ne les certifie pas."), RemoteSafety.openLicence(licence), needsOriginCheck = true)
    }
    fun covers(body: String, releaseId: String, group: Boolean): List<ImageCandidate> {
        if (body.trim() == "null") return emptyList()
        return jsonObject(body).objects("images").filter { it.optBoolean("front") }.take(6).map { item ->
            val url = secureCatalogueUrl(item.str("image"))
            val thumbnail = secureCatalogueUrl(item.optJSONObject("thumbnails")?.str("250").orEmpty().ifBlank { item.optJSONObject("thumbnails")?.str("small").orEmpty().ifBlank { url } })
            ImageCandidate("caa:${item.str("id")}", if (group) "Pochette représentative du groupe, édition à vérifier" else "Pochette de l’édition choisie", thumbnail, url,
                Credit("Cover Art Archive", "https://musicbrainz.org/${if (group) "release-group" else "release"}/$releaseId/cover-art", "Pas de licence générale fournie", details = item.str("comment") + " · Approbation communautaire ≠ droits d’utilisation."), licenceKnown = false, needsOriginCheck = true)
        }
    }
    fun fanart(body: String, id: String, artist: Boolean): List<ImageCandidate> {
        val root = jsonObject(body)
        val images = if (artist) root.objects("artistthumb") else root.objects("albums").filter { it.str("release_group_id") == id }.flatMap { it.objects("albumcover") }
        return images.take(6).map { image ->
            val original = image.str("url")
            ImageCandidate("fanart:${image.str("id")}", if (artist) "Portrait proposé, sujet/date à vérifier" else "Pochette de groupe, édition à vérifier", original.replace("/fanart/", "/preview/"), original, Credit("fanart.tv", "https://fanart.tv/", image.str("copyright").ifBlank { "Copyright inconnu" }, details = image.str("copyright_link") + "\nLa date d’ajout et les likes ne sont pas la date de prise de vue."), false, needsOriginCheck = true)
        }
    }
}
