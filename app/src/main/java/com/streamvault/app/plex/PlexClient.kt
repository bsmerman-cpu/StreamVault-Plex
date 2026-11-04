package com.streamvault.app.plex

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class PlexPin(val id: Long, val code: String)
internal data class PlexServer(val name: String, val uri: String, val token: String)
internal data class PlexSection(val id: String, val title: String, val type: String)
internal data class PlexItem(
    val key: String,
    val title: String,
    val type: String,
    val summary: String,
    val thumb: String?,
    val year: Int?,
    val parentTitle: String?,
    val grandparentTitle: String?,
    val mediaPart: String?
)

/** Plex account linking and personal-library endpoints. Never writes credentials to logs. */
internal class PlexClient(private val clientId: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun createPin(): PlexPin {
        val json = requestJson("https://plex.tv/api/v2/pins", method = "POST")
        return PlexPin(json.getLong("id"), json.getString("code"))
    }

    fun checkPin(id: Long): String? = requestJson("https://plex.tv/api/v2/pins/$id")
        .optString("authToken").takeIf(String::isNotBlank)

    fun servers(accountToken: String): List<PlexServer> {
        val resources = requestJsonArray(
            "https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=0", accountToken
        )
        return buildList {
            for (i in 0 until resources.length()) {
                val resource = resources.optJSONObject(i) ?: continue
                if (!resource.optString("provides").split(',').contains("server")) continue
                val token = resource.optString("accessToken").ifBlank { accountToken }
                val connections = resource.optJSONArray("connections") ?: continue
                val candidates = (0 until connections.length()).mapNotNull { index ->
                    connections.optJSONObject(index)
                }.filter { it.optString("uri").startsWith("https://") }
                    .sortedByDescending { it.optBoolean("local") }
                candidates.firstOrNull()?.optString("uri")?.takeIf(String::isNotBlank)?.let { uri ->
                    add(PlexServer(resource.optString("name"), uri.trimEnd('/'), token))
                }
            }
        }
    }

    fun sections(server: PlexServer): List<PlexSection> {
        val json = requestJson("${server.uri}/library/sections", token = server.token)
        val dirs = json.optJSONObject("MediaContainer")?.optJSONArray("Directory") ?: JSONArray()
        return (0 until dirs.length()).mapNotNull { i ->
            val dir = dirs.optJSONObject(i) ?: return@mapNotNull null
            val type = dir.optString("type")
            if (type !in setOf("movie", "show")) return@mapNotNull null
            PlexSection(dir.optString("key"), dir.optString("title"), type)
        }
    }

    fun sectionItems(server: PlexServer, section: PlexSection): List<PlexItem> = buildList {
        var start = 0
        while (true) {
            val page = metadata(
                server,
                "/library/sections/${section.id}/all?X-Plex-Container-Start=$start&X-Plex-Container-Size=100"
            )
            addAll(page)
            if (page.size < 100 || size >= 5_000) break
            start += page.size
        }
    }

    fun children(server: PlexServer, item: PlexItem): List<PlexItem> =
        metadata(server, "${item.key}/children")

    fun artwork(server: PlexServer, item: PlexItem): String? = item.thumb?.let { path ->
        url(server.uri, path)
    }

    fun playbackUrl(server: PlexServer, item: PlexItem): String? = item.mediaPart?.let { part ->
        url(server.uri, part)
    }

    fun artworkBytes(server: PlexServer, item: PlexItem): ByteArray? {
        val url = artwork(server, item) ?: return null
        val request = Request.Builder().url(url)
            .header("X-Plex-Token", server.token)
            .header("X-Plex-Client-Identifier", clientId)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.bytes()
        }
    }

    private fun metadata(server: PlexServer, path: String): List<PlexItem> {
        val json = requestJson(url(server.uri, path), token = server.token)
        val records = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: JSONArray()
        return (0 until records.length()).mapNotNull { index ->
            val record = records.optJSONObject(index) ?: return@mapNotNull null
            val part = record.optJSONArray("Media")?.optJSONObject(0)
                ?.optJSONArray("Part")?.optJSONObject(0)?.optString("key")
            PlexItem(
                key = record.optString("key"),
                title = record.optString("title"),
                type = record.optString("type"),
                summary = record.optString("summary"),
                thumb = record.optString("thumb").takeIf(String::isNotBlank),
                year = record.optInt("year").takeIf { it > 0 },
                parentTitle = record.optString("parentTitle").takeIf(String::isNotBlank),
                grandparentTitle = record.optString("grandparentTitle").takeIf(String::isNotBlank),
                mediaPart = part?.takeIf(String::isNotBlank)
            )
        }
    }

    private fun requestJsonArray(url: String, token: String): JSONArray = JSONArray(request(url, token))

    private fun requestJson(url: String, token: String? = null, method: String = "GET"):
        JSONObject = JSONObject(request(url, token, method))

    private fun request(url: String, token: String? = null, method: String = "GET"): String {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
            .header("X-Plex-Product", "StreamVault Plex")
            .header("X-Plex-Client-Identifier", clientId)
        token?.let { builder.header("X-Plex-Token", it) }
        if (method == "POST") builder.post(okhttp3.RequestBody.create(null, ByteArray(0)))
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw PlexRequestException(response.code)
            return response.body?.string() ?: throw PlexRequestException(response.code)
        }
    }

    private fun url(origin: String, path: String): String =
        origin.toHttpUrl().newBuilder().encodedPath(path.substringBefore('?'))
            .apply { path.substringAfter('?', "").takeIf(String::isNotBlank)?.let { encodedQuery(it) } }
            .build().toString()
}

internal class PlexRequestException(val status: Int) : Exception("Plex HTTP $status")
