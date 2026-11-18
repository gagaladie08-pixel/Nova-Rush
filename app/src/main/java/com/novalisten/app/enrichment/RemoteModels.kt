package com.novalisten.app.enrichment

import com.novalisten.app.data.LyricLine
import com.novalisten.app.data.LyricTiming
import com.novalisten.app.data.SavedLyrics
import java.net.InetAddress
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A configured credential is not an access grant or a licence for the returned content. */
enum class SecretSlot(val label: String, val help: String, val website: String) {
    LASTFM("Last.fm · clé API", "Lecture des tags et informations musicales ; aucun scrobbling ni portrait.", "https://www.last.fm/api/account/create"),
    FANART("fanart.tv · clé Project", "Complément de pochettes et dernier recours photo. Aucun VIP requis.", "https://fanart.tv/get-an-api-key/"),
    FANART_PERSONAL("fanart.tv · clé Personal (facultative)", "Peut compléter la clé Project, sans abonnement.", "https://fanart.tv/get-an-api-key/"),
    DISCOGS("Discogs · Personal Access Token", "Recherche de catalogue en lecture seule. Le jeton peut aussi donner accès à votre compte : gardez-le privé.", "https://www.discogs.com/settings/developers"),
    GENIUS("Genius · Client Access Token", "Pas le Client Secret. Recherche de références seulement ; sauvegarde des portraits non activée faute de droits établis.", "https://genius.com/api-clients"),
    AZURE("Azure Translator · clé", "Facultatif : texte standard F0 seulement, avec confirmation avant chaque traduction distante.", "https://learn.microsoft.com/azure/ai-services/translator/"),
    AZURE_REGION("Azure · région (si nécessaire)", "Identifiant comme westeurope, ou vide pour une ressource globale. Ne renseignez pas une URL.", "https://learn.microsoft.com/azure/ai-services/translator/"),
    AUDIODB("TheAudioDB · clé (en réserve)", "Clé conservée, mais aucun appel : accès gratuit de NovaListen et usages encore à confirmer. Ne souscrivez pas d’offre payante.", "https://www.theaudiodb.com/api_apply.php"),
    LRC_CONTACT("lrc.red · courriel de contact", "Pas une clé. Le fournisseur exige un courriel de contact dans l’identification de l’application. Il sera transmis à lrc.red uniquement.", "https://lrc.red/api");

    fun validate(raw: String): String {
        val value = raw.trim()
        require(value.length <= 4096 && value.none { it.code !in 33..126 }) { "Valeur invalide : pas d’espace, de saut de ligne ou de caractère de contrôle." }
        if (this == AZURE_REGION) require(value.isEmpty() || value.matches(Regex("[a-z0-9-]{2,40}"))) { "Indiquez un identifiant de région, pas une adresse Internet." }
        else require(value.isNotEmpty()) { "Le champ est vide." }
        if (this == LRC_CONTACT) require(value.matches(Regex("[^@<>(),;:\\s]+@[^@<>(),;:\\s]+\\.[A-Za-z]{2,}"))) { "Un courriel de contact valide est nécessaire pour lrc.red." }
        return value
    }
}

enum class Provider(val label: String, val host: String, val minIntervalMs: Long = 500) {
    LRCLIB("LRCLIB", "lrclib.net"), LRCRED("lrc.red", "lrc.red"),
    LIRIQO("LiriQo", "api.liriqo-alfarrizi.my.id"), BETTER("Better Lyrics · cache public", "api.betterlyrics.org"),
    TEXTYL("Textyl", "api.textyl.co"), OVH("lyrics.ovh", "api.lyrics.ovh"),
    MUSICBRAINZ("MusicBrainz", "musicbrainz.org", 1100), CAA("Cover Art Archive", "coverartarchive.org"),
    ITUNES("iTunes Search", "itunes.apple.com", 3100),
    WIKIPEDIA_FR("Wikipédia FR", "fr.wikipedia.org"), WIKIPEDIA_EN("Wikipédia EN", "en.wikipedia.org"),
    WIKIDATA("Wikidata", "www.wikidata.org"), COMMONS("Wikimedia Commons", "commons.wikimedia.org"),
    OPENVERSE("Openverse", "api.openverse.org", 1500), LASTFM("Last.fm", "ws.audioscrobbler.com"),
    FANART("fanart.tv", "webservice.fanart.tv"), DISCOGS("Discogs", "api.discogs.com", 1100),
    GENIUS("Genius", "api.genius.com"), AZURE("Azure Translator F0", "api.cognitive.microsofttranslator.com");
    val rateGroup: String get() = if (this in setOf(WIKIPEDIA_FR, WIKIPEDIA_EN, WIKIDATA, COMMONS)) "wikimedia" else host
}

data class MusicQuery(val title: String, val artist: String, val album: String = "", val durationMs: Long = 0) {
    fun checked(): MusicQuery {
        require(title.isNotBlank() && artist.isNotBlank()) { "Indiquez un titre et un artiste." }
        require(listOf(title, artist, album).all { it.length <= 300 && it.none { c -> c.isISOControl() } }) { "Recherche trop longue ou invalide." }
        return copy(title = title.trim(), artist = artist.trim(), album = album.trim())
    }
}

data class Credit(val provider: String, val url: String, val licence: String = "", val author: String = "", val details: String = "", val at: Long = System.currentTimeMillis()) {
    fun text(): String = listOf(provider, url, licence, author, details, "Récupéré le ${java.time.Instant.ofEpochMilli(at)}").filter { it.isNotBlank() }.joinToString("\n")
}

data class LyricCandidate(val id: String, val title: String, val artist: String, val album: String, val durationMs: Long, val credit: Credit, val text: String = "", val remoteIsrc: String? = null, val timing: String = "Texte", val upstream: String = "", val nativeLines: List<LyricLine> = emptyList())
fun LyricCandidate.asSavedLyrics(): SavedLyrics = SavedLyrics(original = text, originalSource = credit.text(), originalTiming = runCatching { LyricTiming.encode(text, nativeLines) }.getOrDefault(""))
data class CatalogCandidate(val id: String, val title: String, val artist: String, val album: String = "", val year: String = "", val durationMs: Long = 0, val releaseId: String? = null, val groupId: String? = null, val credit: Credit, val details: String = "")
data class ArtistCandidate(val id: String, val name: String, val description: String, val credit: Credit, val imageTitle: String? = null, val mbid: String? = null)
data class Biography(val id: String, val title: String, val language: String, val text: String, val credit: Credit, val full: Boolean = false)
data class ImageCandidate(val id: String, val title: String, val preview: String, val original: String, val credit: Credit, val licenceKnown: Boolean, val needsOriginCheck: Boolean = false, val date: String = "Date de prise de vue inconnue")

class RemoteFailure(val safeMessage: String) : Exception(safeMessage)
fun Throwable.publicMessage(): String = when (this) {
    is RemoteFailure -> safeMessage
    is IllegalArgumentException -> message?.takeIf { !it.contains("http", true) } ?: "Paramètres invalides."
    else -> "Service indisponible, réponse invalide ou connexion impossible. Les contenus locaux sont conservés."
}

object RemoteSafety {
    fun https(value: String): URI {
        val uri = runCatching { URI(value) }.getOrNull() ?: throw RemoteFailure("Adresse distante invalide.")
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.port in setOf(-1, 443)) { "Seules les adresses HTTPS publiques sont admises." }
        val host = uri.host.lowercase(Locale.ROOT)
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal")) { "Adresse locale interdite." }
        val numeric = host.removePrefix("[").removeSuffix("]")
        if (numeric.contains(':') || numeric.matches(Regex("[0-9.]+"))) require(publicAddress(InetAddress.getByName(numeric))) { "Adresse non publique interdite." }
        return uri
    }
    fun caaRedirect(value: String): String {
        val secure = secureCatalogueUrl(value)
        val uri = https(secure)
        require((uri.host == "archive.org" || uri.host.endsWith(".archive.org")) && uri.path.matches(Regex(".*/mbid-[a-fA-F0-9-]{36}/index\\.json"))) { "Redirection CAA inattendue." }
        return secure
    }
    fun api(provider: Provider, value: String) { require(https(value).host.equals(provider.host, true)) { "Hôte de fournisseur inattendu." } }
    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        if (b.size == 4) return b[0] !in setOf(0, 10, 127) && !(b[0] == 100 && b[1] in 64..127) && !(b[0] == 169 && b[1] == 254) && !(b[0] == 172 && b[1] in 16..31) && !(b[0] == 192 && (b[1] == 168 || (b[1] == 0 && b[2] in setOf(0, 2)))) && !(b[0] == 198 && (b[1] in 18..19 || (b[1] == 51 && b[2] == 100))) && !(b[0] == 203 && b[1] == 0 && b[2] == 113) && b[0] < 224
        return b.size == 16 && b[0] in 0x20..0x3f // global unicast only; excludes ULA and mapped private IPv4
    }
    fun image(value: String) {
        val host = https(value).host.lowercase(Locale.ROOT)
        val forbidden = listOf("last.fm", "lastfm.freetls.fastly.net", "deezer.com", "dzcdn.net", "tidal.com", "genius.com", "geniususercontent.com", "rapgenius.com", "lastfm-img2.akamaized.net", "discogs.com", "spotify.com", "scdn.co")
        require(forbidden.none { host == it || host.endsWith(".$it") }) { "Conservation d’images non admise pour cette source." }
    }
    fun retryAfter(value: String?, now: Long): Long? {
        if (value == null) return null
        val seconds = value.trim().toLongOrNull()
        if (seconds != null) return now + seconds.coerceIn(0, 604800) * 1000
        return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli().coerceIn(now, now + 604800000) }.getOrNull()
    }
    fun mbid(value: String): String { require(value.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))) { "Identifiant MusicBrainz invalide." }; return value }
    fun qid(value: String): String { require(value.matches(Regex("Q[1-9][0-9]*"))) { "Identifiant Wikidata invalide." }; return value }
    fun openLicence(value: String): Boolean {
        val uri = runCatching { URI(value.trim().lowercase(Locale.ROOT)) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("https", "http") || uri.host !in setOf("creativecommons.org", "www.creativecommons.org") || uri.rawUserInfo != null || uri.port !in setOf(-1, 80, 443)) return false
        val path = uri.path ?: return false
        if (uri.normalize().path != path) return false
        return Regex("^/(?:licenses/(?:by|by-sa)/(?:1\\.0|2\\.0|2\\.5|3\\.0|4\\.0)|publicdomain/(?:zero|mark)/1\\.0)/(?:deed(?:\\.[a-z-]+)?|legalcode(?:\\.[a-z-]+)?)?$").matches(path)
    }
}
