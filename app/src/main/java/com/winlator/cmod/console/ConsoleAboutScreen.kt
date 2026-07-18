package com.winlator.cmod.console

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsoleAboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: PackageManager.NameNotFoundException) {
            "?"
        }
    }
    BackHandler(onBack = onBack)

    ConsoleScaffold(title = "About", onBack = onBack) {
        ConsoleScrollColumn(
            Modifier.padding(bottom = 32.dp),
        ) {
            ConsoleCard {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Winlator Ludashi",
                        color = ConsoleColors.TextPrimary,
                        fontFamily = ConsoleFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Version $version",
                        color = ConsoleColors.TextSecondary,
                        fontFamily = ConsoleFontFamily,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "winlator.org",
                        color = ConsoleColors.AccentBlue,
                        fontFamily = ConsoleFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://winlator.org")),
                                    )
                                },
                            )
                            .padding(vertical = 4.dp),
                    )
                }
            }

            ConsoleSectionLabel("Credits")
            ConsoleCard {
                Column(Modifier.padding(20.dp)) {
                    credit("Winlator / CMOD / Ludashi forks")
                    credit("Wine · Box64 · FEX-Emu")
                    credit("Mesa · DXVK · VKD3D · D8VK")
                    credit("Termux · libadrenotools")
                    credit("dxwrapper · CNC DDraw")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "GLIBC experimental edition fork credits apply.",
                        color = ConsoleColors.TextSecondary,
                        fontFamily = ConsoleFontFamily,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun credit(text: String) {
    Text(
        text,
        color = ConsoleColors.TextPrimary,
        fontFamily = ConsoleFontFamily,
        fontSize = 15.sp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
