package com.winlator.cmod.console

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.winlator.cmod.ControlsEditorActivity
import com.winlator.cmod.inputcontrols.InputControlsManager
import com.winlator.cmod.widget.InputControlsView

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
fun ConsoleControlsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val manager = remember { InputControlsManager(context) }
    var profiles by remember { mutableStateOf(manager.profiles.toList()) }
    var opacity by remember {
        mutableFloatStateOf(prefs.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY))
    }
    var selectedId by remember {
        mutableIntStateOf(profiles.firstOrNull()?.id ?: 0)
    }

    BackHandler(onBack = onBack)

    ConsoleScaffold(title = "Controls", onBack = onBack) {
        ConsoleScrollColumn(Modifier.padding(bottom = 28.dp)) {
                ConsoleSectionLabel("Overlay")
                ConsoleCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Opacity  ${(opacity * 100).toInt()}%",
                            color = ConsoleColors.TextPrimary,
                            fontFamily = ConsoleFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        Slider(
                            value = opacity,
                            onValueChange = {
                                opacity = (kotlin.math.round(it * 20f) / 20f)
                            },
                            onValueChangeFinished = {
                                prefs.edit().putFloat("overlay_opacity", opacity).apply()
                            },
                            valueRange = 0.1f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = ConsoleColors.AccentBlue,
                                activeTrackColor = ConsoleColors.AccentBlue,
                            ),
                        )
                    }
                }

                ConsoleSectionLabel("Profiles")
                profiles.forEach { profile ->
                    ConsoleCard {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    profile.name,
                                    color = ConsoleColors.TextPrimary,
                                    fontFamily = ConsoleFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    if (profile.id == selectedId) "Selected" else "Tap to select",
                                    color = ConsoleColors.TextSecondary,
                                    fontFamily = ConsoleFontFamily,
                                    fontSize = 13.sp,
                                )
                            }
                            Text(
                                "Select",
                                color = ConsoleColors.AccentBlue,
                                fontFamily = ConsoleFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.combinedClickable(onClick = { selectedId = profile.id }),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ConsoleCard {
                    val interaction = remember { MutableInteractionSource() }
                    Text(
                        "Open Controls Editor",
                        color = ConsoleColors.AccentBlue,
                        fontFamily = ConsoleFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = {
                                    val i = Intent(context, ControlsEditorActivity::class.java)
                                    i.putExtra("profile_id", selectedId)
                                    context.startActivity(i)
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
        }
    }
}
