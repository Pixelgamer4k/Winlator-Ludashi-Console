package com.winlator.cmod.console.agent

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import com.winlator.cmod.contentdialog.RepositoryManagerDialog
import com.winlator.cmod.contents.AdrenotoolsManager
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.contents.Downloader
import org.json.JSONArray
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Download / install Adreno (Turnip) drivers and content packs (DXVK, VKD3D, Box64…).
 */
object AgentContentsOps {

    fun listInstalledAdreno(context: Context): String {
        val mgr = AdrenotoolsManager(context)
        val ids = mgr.enumarateInstalledDrivers()
        if (ids.isEmpty()) return "No Adreno/Turnip drivers installed (besides System)."
        return ids.joinToString("\n") { id ->
            val name = mgr.getDriverName(id).ifBlank { id }
            val ver = mgr.getDriverVersion(id)
            "- id=\"$id\" name=\"$name\" version=\"$ver\""
        }
    }

    fun listAdrenoDownloads(context: Context, query: String? = null, limit: Int = 30): String {
        val repos = RepositoryManagerDialog.loadDriverRepos(context, 0)
        val q = query?.trim()?.lowercase().orEmpty()
        val lines = mutableListOf<String>()
        var count = 0
        for (repo in repos) {
            if (count >= limit) break
            val json = Downloader.downloadString(repo.apiUrl) ?: continue
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    if (count >= limit) break
                    val rel = arr.getJSONObject(i)
                    val tag = rel.optString("tag_name", rel.optString("name", "release"))
                    val assets = rel.optJSONArray("assets") ?: continue
                    for (a in 0 until assets.length()) {
                        if (count >= limit) break
                        val asset = assets.getJSONObject(a)
                        val name = asset.optString("name", "")
                        if (!name.lowercase().endsWith(".zip")) continue
                        val url = asset.optString("browser_download_url", "")
                        if (url.isEmpty()) continue
                        val hay = "$tag $name".lowercase()
                        if (q.isNotEmpty() && !hay.contains(q)) continue
                        lines += "- repo=\"${repo.name}\" release=\"$tag\" asset=\"$name\" url=$url"
                        count++
                    }
                }
            } catch (_: Exception) {
            }
        }
        return if (lines.isEmpty()) "No matching driver downloads found."
        else lines.joinToString("\n")
    }

    fun installAdrenoFromUrl(context: Context, url: String): String {
        val u = url.trim()
        if (u.isEmpty()) return "Missing download url."
        val tmp = File(context.cacheDir, "agent_driver_temp.zip")
        if (tmp.exists()) tmp.delete()
        if (!Downloader.downloadFile(u, tmp)) {
            return "Download failed: $u"
        }
        return try {
            val mgr = AdrenotoolsManager(context)
            val name = mgr.installDriver(Uri.fromFile(tmp))
            if (name.isNullOrBlank()) {
                "Download OK but install failed (invalid driver ZIP — needs meta.json)."
            } else {
                buildString {
                    appendLine("SUCCESS: Installed Vulkan/Adreno (Turnip) driver **$name**")
                    appendLine("WHAT_THIS_MEANS: The driver package is on device and selectable as a graphics driver version.")
                    appendLine("NEXT: To use it on a game, call apply_game_settings with graphicsDriverVersion=\"$name\" (and graphicsDriver=wrapper if needed).")
                    appendLine("INSTRUCTION: Explain to the user in plain language that the driver was downloaded and installed, what \"$name\" is, and whether you also assigned it to a game.")
                }
            }
        } finally {
            tmp.delete()
        }
    }

    fun installAdrenoByName(context: Context, query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "Provide a release/asset name or substring."
        // If it looks like a URL, install directly
        if (q.startsWith("http://") || q.startsWith("https://")) {
            return installAdrenoFromUrl(context, q)
        }
        val listing = listAdrenoDownloads(context, query = q, limit = 8)
        val urlLine = listing.lineSequence()
            .firstOrNull { it.contains("url=") }
            ?: return "No downloadable driver matching \"$q\". Try list_adreno_downloads first.\n$listing"
        val url = urlLine.substringAfter("url=").trim()
        return installAdrenoFromUrl(context, url)
    }

    fun removeAdreno(context: Context, driverId: String): String {
        val id = driverId.trim()
        if (id.isEmpty()) return "Missing driver id."
        val mgr = AdrenotoolsManager(context)
        if (!mgr.enumarateInstalledDrivers().contains(id)) {
            return "Driver not installed: $id"
        }
        mgr.removeDriver(id)
        return "Removed Adreno driver: $id"
    }

    fun syncAndListContents(context: Context, typeFilter: String? = null): String {
        val cm = ContentsManager(context)
        val catalogUrl = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("downloadable_contents_url", ContentsManager.REMOTE_PROFILES)
            ?: ContentsManager.REMOTE_PROFILES
        val json = Downloader.downloadString(catalogUrl)
        if (json != null) {
            cm.setRemoteProfiles(json)
        } else {
            cm.syncContents()
        }

        val types = resolveTypes(typeFilter)
        val lines = mutableListOf<String>()
        for (type in types) {
            val profiles = cm.getProfiles(type) ?: continue
            lines += "## ${type}"
            if (profiles.isEmpty()) {
                lines += "(none)"
                continue
            }
            for (p in profiles.take(40)) {
                val status = if (p.remoteUrl == null) "installed" else "downloadable"
                val url = p.remoteUrl?.let { " url=$it" }.orEmpty()
                lines += "- [$status] type=${type} verName=\"${p.verName}\" verCode=${p.verCode}$url"
            }
        }
        return lines.joinToString("\n").ifBlank { "No content packs found." }
    }

    fun installContentPack(context: Context, typeName: String, verName: String): String {
        val type = ContentProfile.ContentType.getTypeByName(typeName.trim())
            ?: return "Unknown type \"$typeName\". Use Wine, Proton, DXVK, VKD3D, Box64, WOWBox64, or FEXCore."
        val want = verName.trim()
        if (want.isEmpty()) return "Missing verName."

        val cm = ContentsManager(context)
        val catalogUrl = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("downloadable_contents_url", ContentsManager.REMOTE_PROFILES)
            ?: ContentsManager.REMOTE_PROFILES
        val json = Downloader.downloadString(catalogUrl)
            ?: return "Failed to fetch contents catalog."
        cm.setRemoteProfiles(json)

        val profile = cm.getProfiles(type)?.firstOrNull {
            it.verName.equals(want, true) || it.verName.contains(want, true)
        } ?: return "No $type pack matching \"$want\". Call list_content_packs first."

        if (profile.remoteUrl == null) {
            return "Already installed: ${profile.verName}-${profile.verCode}"
        }

        val tmp = File(context.cacheDir, "agent_content_${type}_${profile.verCode}.tzst")
        if (tmp.exists()) tmp.delete()
        if (!Downloader.downloadFile(profile.remoteUrl, tmp)) {
            // try alternate extension name doesn't matter for bytes
            return "Download failed for ${profile.verName}: ${profile.remoteUrl}"
        }

        val extracted = AtomicReference<ContentProfile?>(null)
        val extractError = AtomicReference<String?>(null)
        cm.extraContentFile(Uri.fromFile(tmp), object : ContentsManager.OnInstallFinishedCallback {
            override fun onFailed(reason: ContentsManager.InstallFailedReason?, e: Exception?) {
                extractError.set("${reason ?: "ERROR"} ${e?.message ?: ""}".trim())
            }

            override fun onSucceed(p: ContentProfile) {
                extracted.set(p)
            }
        })
        tmp.delete()

        val ready = extracted.get()
            ?: return "Extract failed: ${extractError.get() ?: "unknown"}"

        val installError = AtomicReference<String?>(null)
        val installed = AtomicReference<ContentProfile?>(null)
        cm.finishInstallContent(ready, object : ContentsManager.OnInstallFinishedCallback {
            override fun onFailed(reason: ContentsManager.InstallFailedReason?, e: Exception?) {
                installError.set("${reason ?: "ERROR"} ${e?.message ?: ""}".trim())
            }

            override fun onSucceed(p: ContentProfile) {
                installed.set(p)
            }
        })

        val done = installed.get()
            ?: return "Install failed: ${installError.get() ?: "unknown"}"
        return buildString {
            appendLine("SUCCESS: Installed ${done.type} pack **${done.verName}** (code ${done.verCode})")
            appendLine("WHAT_THIS_MEANS: This content is available for Wine/DXVK/Box64 stacks to use.")
            appendLine("INSTRUCTION: Explain what this pack is for in plain language (e.g. DXVK translates Direct3D to Vulkan).")
        }
    }

    private fun resolveTypes(filter: String?): List<ContentProfile.ContentType> {
        if (filter.isNullOrBlank()) return ContentProfile.ContentType.values().toList()
        val t = ContentProfile.ContentType.getTypeByName(filter.trim())
        return if (t != null) listOf(t) else ContentProfile.ContentType.values().toList()
    }
}
