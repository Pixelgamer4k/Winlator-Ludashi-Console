package com.winlator.cmod.console

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.XrActivity
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.xenvironment.ImageFs
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsoleContainersScreen(
    onBack: () -> Unit,
    onOpenEditor: (containerId: Int?) -> Unit,
    onBindNestedBack: ((() -> Boolean)?) -> Unit = {},
) {
    val context = LocalContext.current
    val manager = remember { ContainerManager(context) }
    var containers by remember { mutableStateOf(manager.containers.toList()) }
    var menuFor by remember { mutableStateOf<Container?>(null) }
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var storageFor by remember { mutableStateOf<Container?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    fun reload() {
        containers = ContainerManager(context).containers.toList()
    }

    fun consumeOverlayBack(): Boolean {
        return when {
            busy != null -> true
            storageFor != null -> {
                storageFor = null
                true
            }
            confirm != null -> {
                confirm = null
                true
            }
            menuFor != null -> {
                menuFor = null
                true
            }
            else -> false
        }
    }

    DisposableEffect(menuFor, confirm, storageFor, busy) {
        onBindNestedBack { consumeOverlayBack() }
        onDispose { onBindNestedBack(null) }
    }

    BackHandler {
        if (!consumeOverlayBack()) onBack()
    }

    ConsoleScaffold(
        title = "Containers",
        onBack = onBack,
        trailing = {
            SoftPill("+", filled = true) {
                if (!ImageFs.find(context).isValid) {
                    Toast.makeText(context, "ImageFS not ready", Toast.LENGTH_SHORT).show()
                } else {
                    onOpenEditor(null)
                }
            }
        },
    ) {
        if (containers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No containers yet",
                    color = ConsoleColors.TextSecondary,
                    fontFamily = ConsoleFontFamily,
                    fontSize = 16.sp,
                )
            }
        } else {
            ConsoleLazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(containers, key = { it.id }) { c ->
                    ContainerRow(
                        container = c,
                        onRun = {
                            if (!XrActivity.isEnabled(context)) {
                                val i = Intent(context, XServerDisplayActivity::class.java)
                                i.putExtra("container_id", c.id)
                                context.startActivity(i)
                            } else {
                                XrActivity.openIntent(context as Activity, c.id, null)
                            }
                        },
                        onMenu = { menuFor = c },
                    )
                }
            }
        }
    }

    menuFor?.let { c ->
        ContainerActionSheet(
            container = c,
            onDismiss = { menuFor = null },
            onEdit = {
                menuFor = null
                onOpenEditor(c.id)
            },
            onDuplicate = {
                menuFor = null
                confirm = ConfirmAction.Duplicate(c)
            },
            onRemove = {
                menuFor = null
                confirm = ConfirmAction.Remove(c)
            },
            onInfo = {
                menuFor = null
                storageFor = c
            },
        )
    }

    confirm?.let { action ->
        ConfirmSheet(
            title = when (action) {
                is ConfirmAction.Duplicate -> "Duplicate container?"
                is ConfirmAction.Remove -> "Remove container?"
            },
            message = when (action) {
                is ConfirmAction.Duplicate ->
                    "Create a copy of “${action.container.name}”."
                is ConfirmAction.Remove ->
                    "Permanently delete “${action.container.name}” and its data."
            },
            confirmLabel = when (action) {
                is ConfirmAction.Duplicate -> "Duplicate"
                is ConfirmAction.Remove -> "Remove"
            },
            danger = action is ConfirmAction.Remove,
            onDismiss = { confirm = null },
            onConfirm = {
                val c = action.container
                confirm = null
                busy = when (action) {
                    is ConfirmAction.Duplicate -> "Duplicating…"
                    is ConfirmAction.Remove -> "Removing…"
                }
                when (action) {
                    is ConfirmAction.Duplicate -> manager.duplicateContainerAsync(c) {
                        busy = null
                        reload()
                    }
                    is ConfirmAction.Remove -> manager.removeContainerAsync(c) {
                        busy = null
                        reload()
                    }
                }
            },
        )
    }

    storageFor?.let { c ->
        StorageInfoSheet(
            container = c,
            onDismiss = { storageFor = null },
        )
    }

    busy?.let { msg ->
        Box(
            Modifier
                .fillMaxSize()
                .background(ConsoleColors.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .shadow(16.dp, ConsoleRowShape)
                    .clip(ConsoleRowShape)
                    .background(ConsoleColors.SurfaceRaised)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                CircularProgressIndicator(color = ConsoleColors.AccentBlue, strokeWidth = 3.dp)
                Spacer(Modifier.height(14.dp))
                Text(msg, color = ConsoleColors.TextSecondary, fontFamily = ConsoleFontFamily, fontSize = 14.sp)
            }
        }
    }
}

private sealed class ConfirmAction {
    abstract val container: Container
    data class Duplicate(override val container: Container) : ConfirmAction()
    data class Remove(override val container: Container) : ConfirmAction()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoftPill(label: String, filled: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Text(
        label,
        color = if (filled) Color.White else ConsoleColors.AccentBlue,
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(ConsoleChipShape)
            .background(if (filled) ConsoleColors.AccentBlue else ConsoleColors.SurfaceRaised)
            .border(0.5.dp, if (filled) Color.Transparent else ConsoleColors.CardStroke, ConsoleChipShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContainerRow(
    container: Container,
    onRun: () -> Unit,
    onMenu: () -> Unit,
) {
    ConsoleCard {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ConsoleColors.AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    container.name.take(1).uppercase(),
                    color = ConsoleColors.AccentBlue,
                    fontFamily = ConsoleFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    container.name,
                    color = ConsoleColors.TextPrimary,
                    fontFamily = ConsoleFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${container.wineVersion} · ${container.screenSize}",
                    color = ConsoleColors.TextSecondary,
                    fontFamily = ConsoleFontFamily,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SoftPill("Run", filled = true, onClick = onRun)
            Spacer(Modifier.width(6.dp))
            SoftPill("•••", onClick = onMenu)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContainerActionSheet(
    container: Container,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onInfo: () -> Unit,
) {
    SheetScaffold(title = container.name, onDismiss = onDismiss) {
        SheetAction("Edit") { onEdit() }
        SheetAction("Duplicate") { onDuplicate() }
        SheetAction("Storage info") { onInfo() }
        SheetAction("Remove", danger = true) { onRemove() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SheetScaffold(title = title, onDismiss = onDismiss) {
        Text(
            message,
            color = ConsoleColors.TextSecondary,
            fontFamily = ConsoleFontFamily,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SoftPill("Cancel", onClick = onDismiss)
            SoftPill(confirmLabel, filled = true) { onConfirm() }
        }
        if (danger) {
            Text(
                "This cannot be undone.",
                color = ConsoleColors.Danger,
                fontFamily = ConsoleFontFamily,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageInfoSheet(container: Container, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var driveC by remember { mutableLongStateOf(0L) }
    var cache by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(0L) }
    var scanning by remember { mutableStateOf(true) }
    val internal = remember { FileUtils.getInternalStorageSize() }

    LaunchedEffect(container.id) {
        scanning = true
        val root = container.rootDir
        val driveCDir = File(root, ".wine/drive_c")
        val cacheDir = File(root, ".cache")
        val d = AtomicLong(0)
        val c = AtomicLong(0)
        FileUtils.getSizeAsync(driveCDir) { size ->
            d.addAndGet(size)
            driveC = d.get()
            total = d.get() + c.get()
        }
        FileUtils.getSizeAsync(cacheDir) { size ->
            c.addAndGet(size)
            cache = c.get()
            total = d.get() + c.get()
        }
        // Allow async walk to progress, then stop spinner
        kotlinx.coroutines.delay(1200)
        driveC = d.get()
        cache = c.get()
        total = d.get() + c.get()
        scanning = false
        kotlinx.coroutines.delay(2000)
        driveC = d.get()
        cache = c.get()
        total = d.get() + c.get()
    }

    val pct = if (internal > 0) ((total.toDouble() / internal) * 100).toInt().coerceIn(0, 100) else 0

    SheetScaffold(title = "Storage", onDismiss = onDismiss) {
        Text(
            container.name,
            color = ConsoleColors.TextSecondary,
            fontFamily = ConsoleFontFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        ConsoleCard {
            Column(Modifier.padding(16.dp)) {
                StorageLine("Drive C", StringUtils.formatBytes(driveC))
                StorageLine("Cache", StringUtils.formatBytes(cache))
                StorageLine("Total", StringUtils.formatBytes(total))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (scanning) {
                        CircularProgressIndicator(
                            Modifier.size(28.dp),
                            color = ConsoleColors.AccentBlue,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Text(
                            "$pct%",
                            color = ConsoleColors.AccentBlue,
                            fontFamily = ConsoleFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "of device storage",
                        color = ConsoleColors.TextSecondary,
                        fontFamily = ConsoleFontFamily,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 16.dp)) {
            SoftPill("Clear cache", filled = true) {
                FileUtils.clear(File(container.rootDir, ".cache"))
                container.putExtra("desktopTheme", null)
                container.saveData()
                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StorageLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = ConsoleColors.TextSecondary, fontFamily = ConsoleFontFamily, fontSize = 14.sp)
        Text(value, color = ConsoleColors.TextPrimary, fontFamily = ConsoleFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(ConsoleColors.Scrim)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(24.dp, ConsoleSheetShape, clip = false)
                .clip(ConsoleSheetShape)
                .background(ConsoleColors.SurfaceRaised)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .consoleInteractiveSwipeDown(onDismiss = onDismiss)
                .padding(top = 16.dp, bottom = 20.dp),
        ) {
            // Drag affordance
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
                    .width(36.dp)
                    .height(5.dp)
                    .clip(ConsoleChipShape)
                    .background(ConsoleColors.CardStroke),
            )
            Text(
                title,
                color = ConsoleColors.TextPrimary,
                fontFamily = ConsoleFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, 0.97f)
    Text(
        label,
        color = if (danger) ConsoleColors.Danger else ConsoleColors.TextPrimary,
        fontFamily = ConsoleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    )
}
