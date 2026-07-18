package com.winlator.cmod.console

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.winlator.cmod.console.compat.CompatApplier
import com.winlator.cmod.console.compat.CompatRules
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.ExeIconExtractor
import com.winlator.cmod.core.FileUtils
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

/**
 * Console Add Game: point at an existing game folder on storage.
 * Creates a desktop shortcut to the main EXE **in place** — never copies or
 * moves the game (Unity needs UnityPlayer.dll / *_Data next to the EXE).
 */
object ShortcutImporter {

    private const val TAG = "ShortcutImporter"

    data class Result(
        val success: Boolean,
        val shortcut: Shortcut? = null,
        val message: String = "",
    )

    fun gamesDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "Winlator/Games")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isSupportedName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".exe") || lower.endsWith(".msi") || lower.endsWith(".bat")
    }

    /**
     * User picked a **folder**. Resolve it on disk and shortcut the best EXE
     * inside — files stay where they are.
     */
    fun importFromTreeUri(
        context: Context,
        treeUri: Uri,
        container: Container,
        onProgress: ((String) -> Unit)? = null,
    ): Result {
        return try {
            val tree = DocumentFile.fromTreeUri(context, treeUri)
                ?: return Result(false, message = "Could not open the selected folder")
            if (!tree.isDirectory) {
                return Result(false, message = "Pick the game folder, not a single file")
            }

            onProgress?.invoke("Finding game…")

            val localFolder = resolveLocalDirectory(context, treeUri, tree)
                ?: return Result(
                    false,
                    message = "Can’t use that location without copying. Pick a folder on your phone storage " +
                        "(e.g. Download/…/GameName), not from cloud or another app’s private area.",
                )

            importGameFolder(context, localFolder, container, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "importFromTreeUri failed", e)
            Result(false, message = e.message ?: "Import failed")
        }
    }

    fun importFromUri(
        context: Context,
        uri: Uri,
        container: Container,
        onProgress: ((String) -> Unit)? = null,
    ): Result {
        return try {
            val displayName = queryDisplayName(context, uri) ?: "Game.exe"
            if (!isSupportedName(displayName)) {
                return Result(false, message = "Pick a game folder (or an .exe inside one)")
            }

            val localPath = resolveLocalPath(context, uri)
            if (localPath != null && localPath.isFile) {
                val parent = localPath.parentFile
                    ?: return Result(false, message = "Could not find the game folder")
                return importGameFolder(context, parent, container, onProgress, preferredExe = localPath)
            }

            Result(
                false,
                message = "Pick the game folder on your phone storage so we can use it in place (no copy).",
            )
        } catch (e: Exception) {
            Result(false, message = e.message ?: "Import failed")
        }
    }

    fun importFromFile(
        context: Context,
        file: File,
        container: Container,
        onProgress: ((String) -> Unit)? = null,
    ): Result {
        return try {
            if (!file.isFile || !isSupportedName(file.name)) {
                return Result(false, message = "Pick an .exe, .msi, or .bat file")
            }
            onProgress?.invoke("Adding ${file.name}…")
            // Exact EXE the user chose — do not auto-pick a sibling.
            createShortcut(context, file, container, "Added ${smartDisplayName(file)}")
        } catch (e: Exception) {
            Result(false, message = e.message ?: "Import failed")
        }
    }

    /**
     * Create a library shortcut to the EXE inside [sourceFolder]. Does not copy.
     */
    fun importGameFolder(
        context: Context,
        sourceFolder: File,
        container: Container,
        onProgress: ((String) -> Unit)? = null,
        preferredExe: File? = null,
    ): Result {
        if (!sourceFolder.isDirectory) {
            return Result(false, message = "Not a game folder")
        }

        onProgress?.invoke("Adding ${sourceFolder.name}…")

        val exe = when {
            preferredExe != null && preferredExe.isFile &&
                preferredExe.parentFile?.canonicalPath == sourceFolder.canonicalPath -> preferredExe
            else -> findBestExeFile(sourceFolder)
        } ?: return Result(
            false,
            message = "No .exe found in “${sourceFolder.name}”. Pick the folder that contains the game EXE.",
        )

        if (!exe.isFile) {
            return Result(false, message = "Game EXE not readable at ${exe.absolutePath}")
        }

        return createShortcut(context, exe, container, "Added ${smartDisplayName(exe)}")
    }

    // —— EXE selection ————————————————————————————————————————————————

    fun findBestExeFile(dir: File): File? {
        val exes = mutableListOf<File>()
        collectExes(dir, exes, depth = 0, maxDepth = 2)
        return pickBestExe(exes, dir.name)
    }

    private fun collectExes(dir: File, out: MutableList<File>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isFile && f.name.lowercase().endsWith(".exe")) {
                out.add(f)
            } else if (f.isDirectory && !shouldSkipDirName(f.name)) {
                collectExes(f, out, depth + 1, maxDepth)
            }
        }
    }

    private fun pickBestExe(exes: List<File>, folderHint: String): File? {
        if (exes.isEmpty()) return null
        return exes.maxWithOrNull(
            compareBy<File> { scoreExeName(it.name, folderHint) }
                .thenBy { it.length() },
        )
    }

    private fun scoreExeName(name: String, folderHint: String): Int {
        val lower = name.lowercase()
        var score = 0
        if (isJunkExeName(lower)) return -1000
        val base = lower.removeSuffix(".exe")
        val hint = folderHint.lowercase().replace(Regex("[^a-z0-9]+"), "")
        val compact = base.replace(Regex("[^a-z0-9]+"), "")
        if (hint.isNotEmpty() && (compact.contains(hint) || hint.contains(compact))) score += 50
        if (lower.contains("launcher")) score -= 5
        if (lower.contains("crash") || lower.contains("handler")) score -= 40
        if (lower.contains("unins") || lower.contains("setup") || lower.contains("redist")) score -= 40
        return score
    }

    private fun isJunkExeName(lower: String): Boolean {
        return lower.contains("unitycrashhandler") ||
            lower.contains("crashhandler") ||
            lower.startsWith("unins") ||
            lower.contains("vcredist") ||
            lower.contains("dxsetup") ||
            lower == "unitycrashhandler64.exe"
    }

    private fun shouldSkipDirName(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "_ost" || lower == ".git" || lower == "__macosx"
    }

    // —— Shortcut ————————————————————————————————————————————————

    private fun createShortcut(
        context: Context,
        file: File,
        container: Container,
        successMessage: String,
    ): Result {
        // Probe before writing so incomplete Unity stubs never enter the library.
        val preProfile = CompatApplier.probeExe(file)
        CompatApplier.incompleteUnityMessage(preProfile)?.let { msg ->
            return Result(false, message = msg)
        }

        val displayName = smartDisplayName(file)
        val winePrefix = "${wineHomePath(context, container)}/.wine"

        val shortcutsDir = container.desktopDir
        if (!shortcutsDir.exists()) shortcutsDir.mkdirs()
        val desktopFile = File(shortcutsDir, "$displayName.desktop")

        PrintWriter(FileWriter(desktopFile)).use { writer ->
            writer.println("[Desktop Entry]")
            writer.println("Name=$displayName")
            writer.println("Exec=env WINEPREFIX=\"$winePrefix\" wine \"${file.absolutePath}\"")
            writer.println("Type=Application")
            writer.println("Icon=$displayName")
            writer.println("container_id:${container.id}")
        }

        val iconDir64 = container.getIconsDir(64)
        if (!iconDir64.exists()) iconDir64.mkdirs()
        val iconDest = File(iconDir64, "$displayName.png")
        val iconExtracted = ExeIconExtractor.extractIcon(file, iconDest)

        val iconsDir = File(Environment.getExternalStorageDirectory(), "Winlator/icons")
        if (!iconsDir.exists()) iconsDir.mkdirs()
        if (iconExtracted) {
            val userIcon = File(iconsDir, "$displayName.png")
            if (!userIcon.exists()) {
                try {
                    FileUtils.copy(iconDest, userIcon)
                } catch (_: Exception) {
                }
            }
        }

        val coversDir = File(Environment.getExternalStorageDirectory(), "Winlator/covers")
        if (!coversDir.exists()) coversDir.mkdirs()

        val shortcut = Shortcut(container, desktopFile)
        val applied = CompatApplier.applyToNewShortcut(shortcut, file)
        val tip = CompatRules.tipForTags(applied.profile.tags)
        val msg = if (tip != null) "$successMessage — $tip" else successMessage
        Log.i(TAG, "Compat ${displayName}: ${applied.profile.summary()}")
        return Result(true, shortcut, msg)
    }

    // —— Path / naming ————————————————————————————————————————————————

    private fun resolveLocalDirectory(context: Context, treeUri: Uri, tree: DocumentFile): File? {
        val fromUtils = FileUtils.getFilePathFromUri(context, treeUri)
        if (!fromUtils.isNullOrBlank()) {
            val f = File(fromUtils)
            if (f.isDirectory) return f
        }
        val name = tree.name
        if (!name.isNullOrBlank()) {
            val root = Environment.getExternalStorageDirectory()
            val candidates = listOf(
                File(root, "Download/$name"),
                File(root, "Winlator/Games/$name"),
                File(root, name),
            )
            for (c in candidates) {
                if (c.isDirectory && findBestExeFile(c) != null) return c
            }
            val download = File(root, "Download")
            download.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    val nested = File(child, name)
                    if (nested.isDirectory && findBestExeFile(nested) != null) return nested
                }
            }
        }
        return null
    }

    private fun resolveLocalPath(context: Context, uri: Uri): File? {
        if ("file".equals(uri.scheme, ignoreCase = true) && uri.path != null) {
            val f = File(uri.path!!)
            if (f.isFile) return f
        }
        val fromUtils = try {
            FileUtils.getFilePathFromUri(context, uri)
        } catch (_: Exception) {
            null
        }
        if (!fromUtils.isNullOrBlank()) {
            val f = File(fromUtils)
            if (f.isFile) return f
        }
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            val last = uri.lastPathSegment ?: return null
            val decoded = Uri.decode(last)
            val candidates = mutableListOf<File>()
            if (decoded.startsWith("/")) candidates.add(File(decoded))
            if (decoded.contains(":")) {
                val rel = decoded.substringAfter(':', "")
                if (rel.isNotEmpty()) {
                    candidates.add(File(Environment.getExternalStorageDirectory(), rel))
                }
            }
            for (c in candidates) {
                if (c.isFile) return c
            }
        }
        return null
    }

    private fun wineHomePath(context: Context, container: Container): String {
        val imagefs = File(context.filesDir, "imagefs").absolutePath.replace('\\', '/')
        val rootPath = container.rootDir.absolutePath.replace('\\', '/')
        return if (rootPath.startsWith(imagefs)) {
            rootPath.substring(imagefs.length).ifEmpty { "/home/xuser" }
        } else {
            "/home/xuser"
        }
    }

    private fun smartDisplayName(file: File): String = smartDisplayName(file.name)

    private fun smartDisplayName(name: String): String {
        var n = name
        val dot = n.lastIndexOf('.')
        if (dot > 0) n = n.substring(0, dot)
        return n.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Game" }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
