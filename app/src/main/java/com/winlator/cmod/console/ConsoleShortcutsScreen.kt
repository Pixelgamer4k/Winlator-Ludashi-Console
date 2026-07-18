package com.winlator.cmod.console

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.XrActivity
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsoleShortcutsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { ContainerManager(context) }
    var shortcuts by remember { mutableStateOf(manager.loadShortcuts().toList()) }

    BackHandler(onBack = onBack)

    ConsoleScaffold(title = "Library", onBack = onBack) {
        if (shortcuts.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No shortcuts yet",
                    color = ConsoleColors.TextSecondary,
                    fontFamily = ConsoleFontFamily,
                    fontSize = 16.sp,
                )
            }
        } else {
            ConsoleLazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(shortcuts, key = { it.file.absolutePath }) { sc ->
                    ConsoleCard {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (!XrActivity.isEnabled(context)) {
                                            val i = Intent(context, XServerDisplayActivity::class.java)
                                            i.putExtra("container_id", sc.container.id)
                                            i.putExtra("shortcut_path", sc.file.path)
                                            i.putExtra("shortcut_name", sc.name)
                                            context.startActivity(i)
                                        } else {
                                            XrActivity.openIntent(context as android.app.Activity, sc.container.id, sc.file.path)
                                        }
                                    },
                                    onLongClick = {
                                        sc.file.delete()
                                        shortcuts = manager.loadShortcuts().toList()
                                    },
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sc.name,
                                    color = ConsoleColors.TextPrimary,
                                    fontFamily = ConsoleFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    sc.container.name,
                                    color = ConsoleColors.TextSecondary,
                                    fontFamily = ConsoleFontFamily,
                                    fontSize = 13.sp,
                                )
                            }
                            Text(
                                "Play",
                                color = ConsoleColors.AccentBlue,
                                fontFamily = ConsoleFontFamily,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
