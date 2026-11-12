package dev.viswesh.vibex.audio

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Independent contract adapter; no Lossless Extension implementation is bundled. */
internal object LosslessProvider {
    // Public, non-secret deployment. Can be changed by maintainers without changing API logic.
    @Volatile var endpoint: String = "https://lossless-extension-five.vercel.app"
    enum class Verification { PROVIDER_REPORTED, CONTAINER_CONFIRMED, DECODER_CONFIRMED, UNVERIFIED, REJECTED }
    data class Evidence(val codec: String, val container: String, val sampleRate: Int?, val bitDepth: Int?, val bitrate: Int?, val decoder: String?, val verification: Verification)
    data class Result(val url: String, val evidence: Evidence, val candidateId: String)
    private val versions = listOf("live", "remix", "acoustic", "instrumental", "karaoke", "cover", "sped up", "slowed", "reverb", "lofi", "lo fi")
    private fun norm(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    @JvmStatic fun matches(title: String, artist: String, duration: Double, candidate: JSONObject): Boolean {
        val sourceTitle = norm(title); val otherTitle = norm(candidate.optString("title")); val sourceArtist = norm(artist)
        if (sourceTitle.isEmpty() || sourceArtist.isEmpty() || sourceTitle != otherTitle) return false
        if (versions.any { Regex("(^| )" + Regex.escape(it) + "( |$)").containsMatchIn(sourceTitle) != Regex("(^| )" + Regex.escape(it) + "( |$)").containsMatchIn(otherTitle) }) return false
        val credits = candidate.optString("artist").split(Regex("\\s*,\\s*|\\s+&\\s+"))
        if (credits.none { norm(it) == sourceArtist }) return false
        val otherDuration = candidate.optDouble("duration", 0.0)
        return duration > 0 && otherDuration > 0 && abs(duration - otherDuration) <= 5.0
    }

    /** Validates the FLAC marker and STREAMINFO, not the whole file or decoder. */
    @JvmStatic fun parseFlacHeader(b: ByteArray): Evidence? {
        if (b.size < 42 || b[0] != 'f'.code.toByte() || b[1] != 'L'.code.toByte() || b[2] != 'a'.code.toByte() || b[3] != 'C'.code.toByte() || (b[4].toInt() and 0x7f) != 0 || b[5].toInt() != 0 || b[6].toInt() != 0 || (b[7].toInt() and 255) != 34) return null
        val sr = ((b[18].toInt() and 255) shl 12) or ((b[19].toInt() and 255) shl 4) or ((b[20].toInt() and 255) shr 4)
        val bits = (((b[20].toInt() and 1) shl 4) or ((b[21].toInt() and 255) shr 4)) + 1
        if (sr !in 8000..768000 || bits !in 4..32) return null
        return Evidence("flac", "flac", sr, bits, null, null, Verification.CONTAINER_CONFIRMED)
    }

    @JvmStatic fun resolve(song: JSONObject?): Result? {
        if (song == null) return null
        val title = song.optString("name").trim(); val duration = song.optDouble("duration", 0.0)
        val primary = song.optJSONObject("artists")?.optJSONArray("primary")
        val artist = primary?.optJSONObject(0)?.optString("name")?.trim().orEmpty()
        if (title.isEmpty() || artist.isEmpty() || duration <= 0) return null
        return try {
            val service = URI(endpoint.trimEnd('/'))
            if (service.scheme != "https" || service.host == null || service.userInfo != null || service.query != null || service.fragment != null) return null
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val tracks = getJson("$service/search?q=$query&quality=LOSSLESS").optJSONArray("tracks") ?: return null
            val candidates = (0 until minOf(tracks.length(), 30)).mapNotNull { tracks.optJSONObject(it) }
                .filter { matches(title, artist, duration, it) }
                .sortedBy { abs(duration - it.optDouble("duration", 0.0)) }
            val selected = candidates.firstOrNull() ?: return null
            val id = selected.optString("id"); if (id.isEmpty() || id.length > 512) return null
            resolvePinned(id)

        } catch (_: Exception) { null }
    }
    /** Resolve the same provider recording on every download retry; never substitute AAC. */
    @JvmStatic fun resolvePinned(id: String?): Result? {
        if (id.isNullOrEmpty() || id.length > 512) return null
        return try {
            val service = URI(endpoint.trimEnd('/'))
            if (service.scheme != "https" || service.host == null || service.userInfo != null || service.query != null || service.fragment != null) return null
            val stream = getJson("$service/stream/${URLEncoder.encode(id, "UTF-8")}?quality=LOSSLESS")
            if (stream.optBoolean("encrypted", false) || stream.optString("manifest", "none") !in listOf("", "none")) return null
            val media = URI(stream.optString("url"))
            if (media.scheme != "https" || media.userInfo != null || media.host != "tracks.monochrome.st" || (media.port != -1 && media.port != 443)) return null
            val evidence = try { probe(media.toString()) ?: return null }
                catch (_: java.net.SocketTimeoutException) {
                    Evidence("unknown", "unknown", null, null, null, null, Verification.UNVERIFIED)
                }
            Result(media.toString(), evidence, id)
        } catch (_: Exception) { null }
    }
    private fun connection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 3500; readTimeout = 3500; instanceFollowRedirects = false
        setRequestProperty("User-Agent", "VIBEX/2")
    }
    private fun getJson(url: String): JSONObject {
        val c = connection(url)
        try {
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) throw IOException("Provider HTTP error")
            val out = ByteArrayOutputStream(); val b = ByteArray(8192)
            c.inputStream.use { input -> while (true) { val n = input.read(b); if (n < 0) break; out.write(b, 0, n); if (out.size() > 1024 * 1024) throw IOException("Provider response too large") } }
            return JSONObject(out.toString(StandardCharsets.UTF_8.name()))
        } finally { c.disconnect() }
    }
    private fun probe(url: String): Evidence? {
        val c = connection(url)
        try {
            c.setRequestProperty("Range", "bytes=0-41")
            if (c.responseCode !in listOf(200, 206)) return null
            if (c.responseCode == 206 && !(c.getHeaderField("Content-Range") ?: "").startsWith("bytes 0-")) return null
            val buffer = ByteArray(42); var count = 0
            c.inputStream.use { input -> while (count < buffer.size) { val n = input.read(buffer, count, buffer.size - count); if (n < 0) break; count += n } }
            if (count != buffer.size) return null
            return parseFlacHeader(buffer)
        } finally { c.disconnect() }
    }
}
