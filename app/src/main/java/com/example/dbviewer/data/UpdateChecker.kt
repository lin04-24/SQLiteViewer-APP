package com.example.dbviewer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

    suspend fun checkForUpdate(currentVersion: String): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/lin04-24/SQLiteViewer-APP/releases/latest")
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                }

                if (connection.responseCode != 200) {
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
