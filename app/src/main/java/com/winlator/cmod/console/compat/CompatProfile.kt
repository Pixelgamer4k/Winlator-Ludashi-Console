package com.winlator.cmod.console.compat

import java.io.File

object CompatTags {
    const val I386 = "i386"
    const val AMD64 = "amd64"
    const val ARM64 = "arm64"
    const val UNITY = "unity"
    const val UNITY_INCOMPLETE = "unity_incomplete"
    const val VCRUN2010 = "vcrun2010"
    const val D3D9 = "d3d9"
    const val D3D11 = "d3d11"
    const val D3D12 = "d3d12"
}

data class CompatProfile(
    val exe: File,
    val machine: GameProbe.Machine,
    val tags: Set<String>,
    val sizeBytes: Long,
) {
    fun has(tag: String): Boolean = tags.contains(tag)

    fun summary(): String {
        val parts = mutableListOf(machine.name)
        parts.addAll(tags.filter { it != CompatTags.I386 && it != CompatTags.AMD64 && it != CompatTags.ARM64 })
        return parts.joinToString(", ")
    }
}
