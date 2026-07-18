package com.winlator.cmod.console.compat

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Offline PE / sibling probe used at Add Game (and first Play migration).
 * Intentionally light — no cloud, no full PE parse.
 */
object GameProbe {

    enum class Machine {
        I386, AMD64, ARM64, UNKNOWN
    }

    fun probe(exe: File): CompatProfile {
        val tags = linkedSetOf<String>()
        val machine = readPeMachine(exe)
        val dir = exe.parentFile

        if (dir != null) {
            val hasUnityPlayer = File(dir, "UnityPlayer.dll").isFile
            val hasDataDir = dir.listFiles()?.any {
                it.isDirectory && it.name.endsWith("_Data", ignoreCase = true)
            } == true

            if (hasUnityPlayer || hasDataDir) {
                tags += CompatTags.UNITY
            }
            if (looksLikeIncompleteUnity(exe, dir, hasUnityPlayer, hasDataDir)) {
                tags += CompatTags.UNITY_INCOMPLETE
            }
            if (File(dir, "GameAssembly.dll").isFile || File(dir, "il2cpp_data").isDirectory) {
                tags += CompatTags.UNITY // IL2CPP still Unity
            }
        }

        val sniff = sniffAsciiMarkers(exe)
        if (sniff.msvcp100 || sniff.msvcr100) tags += CompatTags.VCRUN2010
        if (sniff.d3d9) tags += CompatTags.D3D9
        if (sniff.d3d11) tags += CompatTags.D3D11
        if (sniff.d3d12) tags += CompatTags.D3D12
        if (sniff.unityEngine) tags += CompatTags.UNITY

        when (machine) {
            Machine.I386 -> tags += CompatTags.I386
            Machine.AMD64 -> tags += CompatTags.AMD64
            Machine.ARM64 -> tags += CompatTags.ARM64
            Machine.UNKNOWN -> Unit
        }

        return CompatProfile(
            exe = exe,
            machine = machine,
            tags = tags,
            sizeBytes = exe.length(),
        )
    }

    fun readPeMachine(exe: File): Machine {
        if (!exe.isFile || exe.length() < 0x40) return Machine.UNKNOWN
        return try {
            RandomAccessFile(exe, "r").use { raf ->
                val mz = ByteArray(2)
                raf.readFully(mz)
                if (mz[0] != 'M'.code.toByte() || mz[1] != 'Z'.code.toByte()) {
                    return Machine.UNKNOWN
                }
                raf.seek(0x3C)
                val peOffBuf = ByteArray(4)
                raf.readFully(peOffBuf)
                val peOff = ByteBuffer.wrap(peOffBuf).order(ByteOrder.LITTLE_ENDIAN).int
                if (peOff <= 0 || peOff.toLong() + 6 > exe.length()) return Machine.UNKNOWN
                raf.seek(peOff.toLong())
                val peSig = ByteArray(4)
                raf.readFully(peSig)
                if (peSig[0] != 'P'.code.toByte() || peSig[1] != 'E'.code.toByte()) {
                    return Machine.UNKNOWN
                }
                val machineBuf = ByteArray(2)
                raf.readFully(machineBuf)
                val machine = ByteBuffer.wrap(machineBuf).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                when (machine) {
                    0x14c -> Machine.I386
                    0x8664 -> Machine.AMD64
                    0xAA64 -> Machine.ARM64
                    else -> Machine.UNKNOWN
                }
            }
        } catch (_: Exception) {
            Machine.UNKNOWN
        }
    }

    private fun looksLikeIncompleteUnity(
        exe: File,
        dir: File,
        hasUnityPlayer: Boolean,
        hasDataDir: Boolean,
    ): Boolean {
        if (hasUnityPlayer && hasDataDir) return false
        val name = exe.name.lowercase()
        // Tiny stub shipped without the rest of the Unity install.
        if (!hasUnityPlayer && exe.length() < 2_000_000L) {
            if (name.contains("hollow") || name.contains("silksong") || name.contains("unity")) {
                return true
            }
            // Generic: very small EXE with no UnityPlayer next to a "*_Data" name hint
            val siblingHint = dir.list()?.any {
                it.contains("_Data", ignoreCase = true) || it.equals("UnityPlayer.dll", true)
            } == true
            if (siblingHint && !hasUnityPlayer) return true
            if (exe.length() < 800_000L && !hasDataDir) {
                // Heuristic for launcher stubs that look like games but lack payload
                return sniffAsciiMarkers(exe).unityEngine && !hasUnityPlayer
            }
        }
        if (!hasUnityPlayer && hasDataDir) return true
        return false
    }

    private data class AsciiMarkers(
        val msvcp100: Boolean = false,
        val msvcr100: Boolean = false,
        val d3d9: Boolean = false,
        val d3d11: Boolean = false,
        val d3d12: Boolean = false,
        val unityEngine: Boolean = false,
    )

    /** Scan up to 1MB of the PE for common DLL name strings. */
    private fun sniffAsciiMarkers(exe: File): AsciiMarkers {
        if (!exe.isFile) return AsciiMarkers()
        return try {
            val max = minOf(exe.length(), 1_048_576L).toInt()
            val buf = ByteArray(max)
            RandomAccessFile(exe, "r").use { it.readFully(buf) }
            // Lowercase ASCII for simple contains checks
            val sb = StringBuilder(max)
            for (b in buf) {
                val c = b.toInt() and 0xFF
                if (c in 32..126) sb.append(c.toChar().lowercaseChar())
                else sb.append('\u0000')
            }
            val text = sb.toString()
            AsciiMarkers(
                msvcp100 = text.contains("msvcp100"),
                msvcr100 = text.contains("msvcr100"),
                d3d9 = text.contains("d3d9.dll") || text.contains("d3d9."),
                d3d11 = text.contains("d3d11.dll") || text.contains("d3d11."),
                d3d12 = text.contains("d3d12.dll") || text.contains("d3d12."),
                unityEngine = text.contains("unityplayer") || text.contains("unityengine"),
            )
        } catch (_: Exception) {
            AsciiMarkers()
        }
    }
}
