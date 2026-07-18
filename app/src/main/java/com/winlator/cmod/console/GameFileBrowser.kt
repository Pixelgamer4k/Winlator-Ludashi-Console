package com.winlator.cmod.console

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** One row in the Add Game browser — a folder or a launchable file. */
data class GameBrowserEntry(
    val file: File,
    val isDirectory: Boolean,
)

/**
 * Full-screen Add Game browser with Apple-like rounded rows.
 * Back: parent folder first, then cancel.
 */
@Composable
fun GameFileBrowser(
    initialDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    onCancel: () -> Unit,
    onPickExe: (File) -> Unit,
    onBindNestedBack: ((() -> Boolean)?) -> Unit = {},
) {
    var currentDir by remember { mutableStateOf(initialDir) }
    var entries by remember { mutableStateOf<List<GameBrowserEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload(dir: File) {
        val list = dir.listFiles()
        if (list == null) {
            error = "Can’t read this folder"
            entries = emptyList()
            return
        }
        error = null
        val dirs = list.filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
            .map { GameBrowserEntry(it, true) }
        val exes = list.filter { it.isFile && ShortcutImporter.isSupportedName(it.name) }
            .sortedBy { it.name.lowercase() }
            .map { GameBrowserEntry(it, false) }
        entries = dirs + exes
    }

    LaunchedEffect(currentDir) {
        reload(currentDir)
    }

    val storageRoot = remember { Environment.getExternalStorageDirectory() }
    val parent = currentDir.parentFile
    val canGoUp = parent != null && (
        currentDir.canonicalPath != storageRoot.canonicalPath &&
            currentDir.canonicalPath.startsWith(storageRoot.canonicalPath)
        )

    DisposableEffect(currentDir, canGoUp) {
        onBindNestedBack {
            if (canGoUp && parent != null) {
                currentDir = parent
                true
            } else {
                onCancel()
                true
            }
        }
        onDispose { onBindNestedBack(null) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ConsoleColors.Canvas)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Add Game",
                    color = ConsoleColors.TextPrimary,
                    fontFamily = ConsoleFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Cancel",
                    color = ConsoleColors.AccentBlue,
                    fontFamily = ConsoleFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clip(ConsoleChipShape)
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            Text(
                "Open a folder, then tap the .exe to play",
                color = ConsoleColors.TextSecondary,
                fontFamily = ConsoleFontFamily,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                currentDir.absolutePath.removePrefix(storageRoot.absolutePath).ifEmpty { "/" }
                    .let { if (it.startsWith("/")) it else "/$it" },
                color = ConsoleColors.TextSecondary.copy(alpha = 0.85f),
                fontFamily = ConsoleFontFamily,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))

            if (canGoUp) {
                BrowserRow(
                    label = "Parent folder",
                    subtitle = parent!!.name.ifEmpty { "Storage" },
                    kind = "DIR",
                    onClick = { currentDir = parent!! },
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickChip("Download") {
                    currentDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                }
                QuickChip("Games") {
                    currentDir = File(storageRoot, "Winlator/Games").also { if (!it.exists()) it.mkdirs() }
                }
                QuickChip("Storage") {
                    currentDir = storageRoot
                }
            }
            Spacer(Modifier.height(14.dp))

            when {
                error != null -> Text(
                    error!!,
                    color = ConsoleColors.Danger,
                    fontFamily = ConsoleFontFamily,
                    fontSize = 14.sp,
                )
                entries.isEmpty() -> Text(
                    "No folders or .exe files here",
                    color = ConsoleColors.TextSecondary,
                    fontFamily = ConsoleFontFamily,
                    fontSize = 14.sp,
                )
                else -> ConsoleLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries, key = { it.file.absolutePath }) { entry ->
                        if (entry.isDirectory) {
                            BrowserRow(
                                label = entry.file.name,
                                subtitle = "Folder",
                                kind = "DIR",
                                onClick = { currentDir = entry.file },
                            )
                        } else {
                            BrowserRow(
                                label = entry.file.name,
                                subtitle = formatSize(entry.file.length()),
                                kind = "EXE",
                                onClick = { onPickExe(entry.file) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Text(
        label,
        color = ConsoleColors.AccentBlue,
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ConsoleChipShape)
            .background(ConsoleColors.SurfaceRaised)
            .border(0.5.dp, ConsoleColors.CardStroke, ConsoleChipShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun BrowserRow(
    label: String,
    subtitle: String,
    kind: String,
    onClick: () -> Unit,
) {
    val isExe = kind == "EXE"
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale = 0.97f)

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ConsoleRowShape)
            .background(ConsoleColors.SurfaceRaised)
            .border(
                0.5.dp,
                if (isExe) ConsoleColors.AccentBlue.copy(alpha = 0.28f) else ConsoleColors.CardStroke,
                ConsoleRowShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(ConsoleChipShape)
                .background(
                    if (isExe) ConsoleColors.AccentBlue.copy(alpha = 0.12f)
                    else ConsoleColors.CanvasDeep,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                kind,
                color = if (isExe) ConsoleColors.AccentBlue else ConsoleColors.TextSecondary,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = ConsoleColors.TextPrimary,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = ConsoleColors.TextSecondary,
                fontFamily = ConsoleFontFamily,
                fontSize = 13.sp,
            )
        }
        Text(
            if (kind == "DIR") "›" else "+",
            color = if (isExe) ConsoleColors.AccentBlue else ConsoleColors.TextSecondary,
            fontFamily = ConsoleFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
