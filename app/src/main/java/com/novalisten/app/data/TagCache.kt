package com.novalisten.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Mémoire des tags lus dans les fichiers, conservée entre deux démarrages.
 *
 * Relire les tags internes de chaque morceau coûte une ouverture de fichier par morceau : c'est
 * la principale raison du démarrage lent. La clé d'invalidation est la seule qui compte :
 * **la taille du fichier et sa date de modification**. Ni le numéro MediaStore ni le chemin n'en
 * font partie — ils changent après un simple redémarrage du téléphone, ce qui vidait le cache à
 * chaque fois sans qu'aucun fichier n'ait bougé.
 *
 * L'écriture est groupée et l'ensemble est borné : le fichier ne peut pas grossir indéfiniment.
 */
internal class TagCache(context: Context, private val limit: Int = 6000) {
    private val file = File(context.filesDir, "nova-tag-cache.json")
    private val entries = LinkedHashMap<String, Pair<String, NativeCatalogTags>>()
    private var dirty = false
    private var writes = 0

    init { load() }

    operator fun get(id: String): Pair<String, NativeCatalogTags>? = entries[id]

    fun put(id: String, revision: String, tags: NativeCatalogTags) {
        if (entries.size >= limit && !entries.containsKey(id)) {
            // Retire l'entrée la plus ancienne : le cache est un accélérateur, pas une archive.
            entries.keys.firstOrNull()?.let(entries::remove)
        }
        entries[id] = revision to tags
        dirty = true
        // Écrire à chaque morceau ferait payer le disque à chaque fichier. On regroupe.
        if (++writes % 120 == 0) flush()
    }

    fun flush() {
        if (!dirty) return
        dirty = false
        val list = JSONArray()
        entries.forEach { (id, value) ->
            val tags = value.second
            list.put(JSONObject().apply {
                put("id", id); put("rev", value.first)
                put("rg", tags.releaseGroupId); put("rel", tags.releaseId)
                put("kind", tags.kind.name); put("rec", tags.recordingId); put("isrc", tags.isrc)
                put("disc", tags.disc); put("year", tags.originalYear)
                put("aa", tags.albumArtist)
                put("cr", JSONArray().apply { tags.credits.forEach { put(it.toJson()) } })
                put("acr", JSONArray().apply { tags.albumCredits.forEach { put(it.toJson()) } })
            })
        }
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(list.toString())
            if (!temp.renameTo(file)) temp.copyTo(file, overwrite = true)
        }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val list = JSONArray(file.readText())
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val id = o.optString("id"); if (id.isBlank()) continue
                entries[id] = o.optString("rev") to NativeCatalogTags(
                    credits = o.optJSONArray("cr").credits(),
                    releaseGroupId = o.optString("rg"), releaseId = o.optString("rel"),
                    kind = runCatching { ReleaseKind.valueOf(o.optString("kind")) }.getOrDefault(ReleaseKind.UNKNOWN),
                    recordingId = o.optString("rec"), isrc = o.optString("isrc"),
                    disc = o.optInt("disc"), originalYear = o.optInt("year"),
                    albumArtist = o.optString("aa"), albumCredits = o.optJSONArray("acr").credits(),
                )
            }
        }.onFailure {
            // Un cache illisible ne doit jamais empêcher l'application de démarrer : on repart vide.
            runCatching { file.delete() }
            entries.clear()
        }
    }

    private fun ArtistCredit.toJson() = JSONObject().apply { put("n", name); put("m", mbid) }

    private fun JSONArray?.credits(): List<ArtistCredit> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            val o = optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("n"); if (name.isBlank()) null else ArtistCredit(name, o.optString("m"))
        }
    }

    fun size(): Int = entries.size
}
