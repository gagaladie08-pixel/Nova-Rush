package com.novalisten.app.enrichment

import com.novalisten.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface RemoteTransport {
    suspend fun request(provider: Provider, path: String, parameters: Map<String, String> = emptyMap(), headers: Map<String, String> = emptyMap(), jsonBody: String? = null): String
    suspend fun image(url: String, maxBytes: Int = 8_000_000): ByteArray
}

/** One bounded scheduler, no cookies, no disk cache, no automatic retry or credential-bearing redirect. */
class RemoteHttp : RemoteTransport {
    private val limiter = RequestLimiter()
    private val requests = java.util.concurrent.atomic.AtomicInteger()
    val requestCount: Int get() = requests.get()
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                if (addresses.isEmpty() || addresses.any { !RemoteSafety.publicAddress(it) }) throw IOException("Adresse non publique")
            }
        }).build()
    private val userAgent = "NovaListen/${BuildConfig.VERSION_NAME} (https://github.com/gagaladie08-pixel/Nova-Rush)"

    override suspend fun request(provider: Provider, path: String, parameters: Map<String, String>, headers: Map<String, String>, jsonBody: String?): String {
        require(path.startsWith('/') && !path.startsWith("//") && !path.contains('?') && !path.contains('#'))
        val url = "https://${provider.host}$path".toHttpUrl().newBuilder().apply { parameters.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        RemoteSafety.api(provider, url.toString())
        val request = Request.Builder().url(url).header("Accept", "application/json, text/plain;q=0.9").header("User-Agent", userAgent)
            .apply { headers.forEach { (key, value) -> header(key, value) }; if (jsonBody != null) post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())) }.build()
        var reply = limiter.execute(provider.rateGroup, provider.minIntervalMs) { execute(request, 2_000_000).also { recordCooldown(provider.rateGroup, it) } }
        if (provider == Provider.CAA && reply.code in 300..399) {
            // CAA documents an index.json redirect to Internet Archive. No credentials cross this boundary.
            var location = reply.location ?: throw RemoteFailure("CAA : redirection sans adresse.")
            var hops = 0
            while (reply.code in 300..399 && hops++ < 4) {
                val destination = RemoteSafety.caaRedirect(location)
                val safeRequest = Request.Builder().url(destination).header("Accept", "application/json").header("User-Agent", userAgent).build()
                reply = limiter.execute(provider.rateGroup, provider.minIntervalMs) { execute(safeRequest, 2_000_000).also { recordCooldown(provider.rateGroup, it) } }
                location = java.net.URI(destination).resolve(reply.location.orEmpty()).toString()
            }
        }
        val now = System.currentTimeMillis()
        if (reply.code == 429 || reply.code == 503) {
            val until = RemoteSafety.retryAfter(reply.retryAfter, now) ?: (now + if (reply.code == 429) 60_000 else 30_000)
            limiter.cooldown(provider.rateGroup, until)
            throw RemoteFailure("${provider.label} : ${if (reply.code == 429) "limite atteinte" else "temporairement indisponible"}. Réessayez après ${java.time.Instant.ofEpochMilli(until)}.")
        }
        when (reply.code) {
            401, 403 -> throw RemoteFailure(if (provider == Provider.BETTER) "Better Lyrics : accès protégé ou résultat hors cache. Aucune clé privée n’est utilisée." else "${provider.label} : accès refusé. Vérifiez la configuration ; ce n’est pas une absence de résultat.")
            404 -> return if (provider in setOf(Provider.CAA, Provider.LRCLIB)) "null" else throw RemoteFailure("${provider.label} : ressource ou route introuvable.")
            in 300..399 -> throw RemoteFailure("${provider.label} : redirection refusée pour protéger les identifiants.")
            !in 200..299 -> throw RemoteFailure("${provider.label} : erreur HTTP ${reply.code}. Vos contenus restent inchangés.")
        }
        return reply.bytes.toString(Charsets.UTF_8)
    }
    override suspend fun image(url: String, maxBytes: Int): ByteArray {
        var current = url
        repeat(5) {
            RemoteSafety.image(current)
            val host = RemoteSafety.https(current).host
            val request = Request.Builder().url(current).header("User-Agent", userAgent).header("Accept", "image/*").build()
            val group = if (host == "wikimedia.org" || host.endsWith(".wikimedia.org")) "wikimedia" else host
            val reply = limiter.execute(group, 500) { execute(request, maxBytes.coerceAtMost(8_000_000)).also { recordCooldown(group, it) } }
            if (reply.code in setOf(301, 302, 303, 307, 308)) {
                current = secureCatalogueUrl(RemoteSafety.https(current).resolve(reply.location ?: throw RemoteFailure("Redirection d’image invalide.")).toString())
            } else {
                if (reply.code == 429 || reply.code == 503) {
                    limiter.cooldown(group, RemoteSafety.retryAfter(reply.retryAfter, System.currentTimeMillis()) ?: (System.currentTimeMillis() + 60_000))
                }
                if (reply.code !in 200..299 || !reply.contentType.startsWith("image/")) throw RemoteFailure("Image indisponible, refusée ou quota atteint.")
                return reply.bytes
            }
        }
        throw RemoteFailure("Trop de redirections pour cette image.")
    }
    private fun recordCooldown(group: String, reply: Reply) {
        if (reply.code == 429 || reply.code == 503) {
            val until = RemoteSafety.retryAfter(reply.retryAfter, System.currentTimeMillis()) ?: (System.currentTimeMillis() + 60_000)
            limiter.cooldown(group, until)
        }
    }
    private data class Reply(val code: Int, val bytes: ByteArray, val retryAfter: String?, val location: String?, val contentType: String)
    private suspend fun execute(request: Request, maxBytes: Int): Reply = suspendCancellableCoroutine { continuation ->
        requests.incrementAndGet()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(RemoteFailure("Connexion impossible ou interrompue. Aucun contenu local n’a été modifié."))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val reply = response.use {
                        val output = ByteArrayOutputStream()
                        // Redirect/error bodies are unnecessary and may contain untrusted diagnostics.
                        if (response.isSuccessful) response.body?.use { body ->
                            if (body.contentLength() > maxBytes) throw RemoteFailure("Réponse trop volumineuse ; téléchargement arrêté.")
                            body.byteStream().use { stream ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val n = stream.read(buffer)
                                    if (n < 0) break
                                    if (output.size() + n > maxBytes) throw RemoteFailure("Réponse trop volumineuse ; téléchargement arrêté.")
                                    output.write(buffer, 0, n)
                                }
                            }
                        }
                        Reply(response.code, output.toByteArray(), response.header("Retry-After"), response.header("Location"), response.header("Content-Type").orEmpty())
                    }
                    if (continuation.isActive) continuation.resume(reply)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(if (e is RemoteFailure || e is CancellationException) e else RemoteFailure("Réponse distante illisible ou incomplète."))
                }
            }
        })
    }
}
