package com.example.dbviewer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String
)

data class UpdateInfo(
    val version: String,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String
)

class UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val RELEASES_API_URL = "https://api.github.com/repos/lin04-24/SQLiteViewer-APP/releases/latest"
        const val RELEASES_ATOM_URL = "https://github.com/lin04-24/SQLiteViewer-APP/releases.atom"
        const val LATEST_APK_URL = "https://github.com/lin04-24/SQLiteViewer-APP/releases/latest/download/SQLiteViewer.apk"
        const val USER_AGENT = "SQLiteViewer-Android"
    }

    suspend fun checkForUpdate(currentVersion: String): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(RELEASES_API_URL)
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", USER_AGENT)
                }

                if (connection.responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    return@runCatching checkFromAtom(currentVersion)
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP ${connection.responseCode}")
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<GitHubRelease>(response)

                // Extract version number (remove 'v' prefix if present)
                val latestVersion = release.tag_name.removePrefix("v")

                // Compare versions
                if (isNewerVersion(latestVersion, currentVersion)) {
                    // Find APK download URL
                    val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                        ?: throw Exception("未找到 APK 文件")

                    UpdateInfo(
                        version = release.tag_name,
                        versionName = release.name.ifBlank { release.tag_name },
                        changelog = release.body,
                        downloadUrl = apkAsset.browser_download_url
                    )
                } else {
                    null // No update available
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /** The API is subject to a small unauthenticated quota; the public Atom feed is not. */
    private fun checkFromAtom(currentVersion: String): UpdateInfo? {
        val connection = URL(RELEASES_ATOM_URL).openConnection() as HttpURLConnection
        try {
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/atom+xml")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub 更新服务暂时不可用")
            }

            val parser = Xml.newPullParser().apply {
                setInput(connection.inputStream.bufferedReader())
            }
            var event = parser.eventType
            var inEntry = false
            var inContent = false
            var tag = ""
            var version: String? = null
            var title = ""
            var changelog = ""
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name
                        if (tag == "entry") {
                            inEntry = true
                            version = null
                            title = ""
                            changelog = ""
                            inContent = false
                        } else if (inEntry && tag == "link" && parser.getAttributeValue(null, "rel") == "alternate") {
                            version = parser.getAttributeValue(null, "href")?.substringAfterLast("/tag/")
                        } else if (inEntry && tag == "content") {
                            inContent = true
                        }
                    }
                    XmlPullParser.TEXT -> if (inEntry) {
                        when (tag) {
                            "title" -> title += parser.text
                            "content" -> changelog += parser.text
                            else -> if (inContent) changelog += parser.text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "content") {
                            inContent = false
                        } else if (parser.name == "entry") {
                            val latestVersion = version?.removePrefix("v")
                            if (!latestVersion.isNullOrBlank()) {
                                return if (isNewerVersion(latestVersion, currentVersion)) {
                                    UpdateInfo(
                                        version = version!!,
                                        versionName = title.ifBlank { version!! },
                                        changelog = changelog.replace(Regex("<[^>]+>"), "").trim(),
                                        downloadUrl = LATEST_APK_URL,
                                    )
                                } else {
                                    null
                                }
                            }
                            inEntry = false
                        }
                    }
                }
                event = parser.next()
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val remotePart = remoteParts.getOrNull(i) ?: 0
            val localPart = localParts.getOrNull(i) ?: 0

            when {
                remotePart > localPart -> return true
                remotePart < localPart -> return false
            }
        }

        return false
    }
}
