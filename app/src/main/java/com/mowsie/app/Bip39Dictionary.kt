package com.mowsie.app

import android.content.Context
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import java.io.File
import java.util.Locale

/** A local-only English BIP39 dictionary. Derived from the same vetted BIP39 library
 * already used by GenesisWords, so no network or additional dependency is needed.
 * The nonsecret word list is cached in noBackupFilesDir after its first construction.
 */
object Bip39Dictionary {
    @Volatile private var Words: List<String>? = null

    fun IsReady(): Boolean = Words != null

    @Synchronized fun Load(Context: Context): List<String> {
        Words?.let { return it }
        val Cache = File(Context.noBackupFilesDir, "mowsie_bip39_words.txt")
        if (Cache.exists()) {
            val Existing = Cache.readLines().filter { it.isNotBlank() }
            if (Existing.size == 2048 && Existing.first() == "abandon" && Existing.last() == "zoo" &&
                Existing.toSet().size == 2048 && Existing == Existing.sorted()) {
                Words = Existing
                return Existing
            }
        }
        // BIP39's first word is indexed by the top 11 bits of 128-bit entropy.
        val Built = (0 until 2048).map { Index ->
            val Entropy = ByteArray(16)
            Entropy[0] = (Index ushr 3).toByte()
            Entropy[1] = ((Index and 7) shl 5).toByte()
            val Mnemonic = MnemonicCode(Entropy)
            try {
                val First: Any = Mnemonic.first()
                if (First is CharArray) String(First).lowercase(Locale.ROOT)
                else First.toString().lowercase(Locale.ROOT)
            } finally {
                Mnemonic.clear()
                Entropy.fill(0)
            }
        }
        check(Built.size == 2048 && Built.first() == "abandon" && Built.last() == "zoo" &&
            Built.toSet().size == 2048 && Built == Built.sorted()) {
            "Unable to reconstruct the canonical BIP39 wordlist"
        }
        Cache.writeText(Built.joinToString("\n", postfix = "\n"))
        Words = Built
        return Built
    }

    fun IsWord(Context: Context, Input: String): Boolean =
        Load(Context).binarySearch(Input.lowercase(Locale.ROOT)) >= 0

    fun Completion(Context: Context, Input: String): String? {
        val Prefix = Input.lowercase(Locale.ROOT)
        if (Prefix.isEmpty()) return null
        val Words = Load(Context)
        if (Prefix.length < 4) return Prefix.takeIf { Words.binarySearch(it) >= 0 }
        return Words.firstOrNull { it.startsWith(Prefix) }
    }

    fun HasLonger(Context: Context, Input: String): Boolean =
        Load(Context).any { it.length > Input.length && it.startsWith(Input.lowercase(Locale.ROOT)) }

    fun ParsePasted(Context: Context, Raw: String): List<String>? {
        val Normalized = Raw.lowercase(Locale.ROOT).trim()
        val Separated = Normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (Separated.size == 12) {
            val Completed = Separated.map { Completion(Context, it) ?: return null }
            if (Completed.any { !IsWord(Context, it) }) return null
            return Completed
        }
        if (Separated.size != 1 || !Normalized.all { it in 'a'..'z' }) return null
        // No-space paste. Only resolve if exactly one checksum-valid segmentation exists.
        val Words = Load(Context)
        val ByFirst = Words.groupBy { it.first() }
        val Candidates = mutableListOf<List<String>>()
        val Path = mutableListOf<String>()
        var Visited = 0
        fun Walk(Offset: Int) {
            if (Candidates.size > 16 || Visited++ > 20000) return
            if (Path.size == 12) {
                if (Offset == Normalized.length) {
                    try {
                        val Entropy = GenesisWords.ToEntropy(Path)
                        Entropy.fill(0)
                        Candidates.add(Path.toList())
                    } catch (_: Exception) { /* checksum did not match */ }
                }
                return
            }
            if (Offset >= Normalized.length) return
            for (Word in ByFirst[Normalized[Offset]].orEmpty()) {
                if (Normalized.startsWith(Word, Offset)) {
                    Path.add(Word)
                    Walk(Offset + Word.length)
                    Path.removeAt(Path.lastIndex)
                }
            }
        }
        Walk(0)
        return Candidates.singleOrNull()
    }
}
