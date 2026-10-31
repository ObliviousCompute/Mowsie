package com.mowsie.app

/** Public display identity only. QR structure is validated, not ownership. */
internal data class ScanIdentity(val Name: String, val PublicKey: String) {
    val ShortKey get() = "${PublicKey.take(4)}…${PublicKey.takeLast(4)}"
}

internal object ScanProtocol {
    fun Recognize(Payload: String): ScanIdentity? {
        val Lines = Payload.replace("\r", "").lines()
        if (Lines.firstOrNull() != "MOWSIE PUBLIC KEY V1") return null
        if (Lines.count { it == "Algorithm: Ed25519" } != 1) return null
        if (Lines.count { it == "Derivation: m/0'/0'" } != 1) return null
        val Names = Lines.filter { it.startsWith("Stash: ") }
        val Keys = Lines.filter { it.startsWith("Public Key: ") }
        if (Names.size != 1 || Keys.size != 1) return null
        val Name = Names.single().removePrefix("Stash: ").trim()
        val Key = Keys.single().removePrefix("Public Key: ").trim()
        if (Name.isEmpty() || Name.length > 64 || Name.any { java.lang.Character.isISOControl(it) }) return null
        if (!Key.matches(Regex("[0-9a-fA-F]{64}"))) return null
        return ScanIdentity(Name, Key.uppercase(java.util.Locale.ROOT))
    }
}

