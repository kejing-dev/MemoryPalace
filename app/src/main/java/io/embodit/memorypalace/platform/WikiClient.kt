package io.embodit.memorypalace.platform

import android.content.Context
import io.embodit.memorypalace.domain.WikiBuilder
import io.embodit.memorypalace.domain.WikiHit
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wikipedia could not be reached (timeout, DNS, HTTP ≥ 400): design S31. */
class WikiUnreachable(message: String) : Exception(message)

/**
 * Wikipedia's REST API (design `contentSources.wikipedia.endpoints`), the app's only network use:
 * title search and the Parsoid HTML of one article. Every request names the app (User-Agent, API
 * etiquette), asks for Simplified Chinese on zh (`Accept-Language: zh-cn`) and gives up after 6 s.
 * Nothing is cached; a built palace is stored by the app and needs no network afterwards.
 *
 * Debug builds can replace the network: [failAll] makes every request fail (S31), [fixtures] answers from
 * `assets/wiki/<lang>-search-<query>.json` and `assets/wiki/<lang>-<title>.html` (debug source set only).
 */
class WikiClient(private val context: Context) {
    @Volatile var failAll = false

    @Volatile var fixtures = false

    suspend fun search(lang: String, query: String): List<WikiHit> {
        val json = get(lang, "search-$query.json", "https://$lang.wikipedia.org/w/rest.php/v1/search/title?q=${enc(query)}&limit=8")
        return WikiBuilder.hits(json)
    }

    /** Can this network reach Wikipedia at all? (S29 or S31 when the page opens.) */
    suspend fun probe(lang: String) {
        if (fixtures && !failAll) return
        get(lang, "probe", "https://$lang.wikipedia.org/w/rest.php/v1/search/title?q=a&limit=1")
    }

    suspend fun article(lang: String, title: String): String = get(lang, "$title.html", "https://$lang.wikipedia.org/w/rest.php/v1/page/${enc(title.replace(' ', '_'))}/html")

    private suspend fun get(lang: String, fixture: String, url: String): String =
        withContext(Dispatchers.IO) {
            if (failAll) throw WikiUnreachable("debug wikiFail")
            if (fixtures) {
                val name = "wiki/$lang-$fixture"
                PalaceLog.i("WIKI fixture $name")
                return@withContext runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrElse {
                    // a search the fixtures do not know finds nothing (as the real API would); a missing article fails
                    if (fixture.startsWith("search-")) """{"pages":[]}""" else throw WikiUnreachable("no fixture $name")
                }
            }
            val t0 = System.currentTimeMillis()
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", USER_AGENT)
                c.setRequestProperty("Accept-Language", if (lang == "zh") "zh-cn" else lang)
                val code = c.responseCode
                PalaceLog.i("WIKI GET $url -> $code ${System.currentTimeMillis() - t0} ms")
                if (code >= 400) throw WikiUnreachable("HTTP $code")
                c.inputStream.bufferedReader().use { it.readText() }
            } catch (e: WikiUnreachable) {
                throw e
            } catch (e: Exception) {
                PalaceLog.w("WIKI failed $url: $e")
                throw WikiUnreachable(e.toString())
            } finally {
                c.disconnect()
            }
        }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        const val TIMEOUT_MS = 6_000
        // Wikimedia asks for a contact in the User-Agent: the design leaves it to be filled in before release
        const val USER_AGENT = "MemoryPalace/0.2 (io.embodit.memorypalace)"
    }
}
