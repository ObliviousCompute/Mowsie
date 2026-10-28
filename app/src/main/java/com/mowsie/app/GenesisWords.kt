package com.mowsie.app

import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import java.util.Locale

/** The external library handles the official English BIP39 2048-word list and checksum. */
object GenesisWords {

    fun FromEntropy(Entropy: ByteArray): List<String> {
        require(Entropy.size == 16)
        val Code = MnemonicCode(Entropy)
        return try {
            val Words = mutableListOf<String>()
            for (Word in Code) {
                val Value: Any = Word
                Words.add(
                    (if (Value is CharArray) String(Value) else Value.toString())
                        .lowercase(Locale.ROOT)
                )
            }
            check(Words.size == 12) { "Incorrect BIP39 word count" }
            Words
        } finally {
            Code.clear()
        }
    }

    fun ToEntropy(Input: List<String>): ByteArray {
        require(Input.size == 12) { "Enter exactly 12 words" }
        val Phrase = Input.joinToString(" ") { it.lowercase(Locale.ROOT).trim() }
        val Code = MnemonicCode(Phrase)
        return try {
            Code.validate()
            Code.toEntropy().also { require(it.size == 16) }
        } finally {
            Code.clear()
        }
    }

    fun SelfTest() {
        // Official BIP39 128-bit zero-entropy vector.
        val ZeroEntropy = ByteArray(16)
        val Expected = List(11) { "abandon" } + "about"
        val Actual = FromEntropy(ZeroEntropy)
        check(Actual == Expected) { "BIP39 known-vector test failed" }
        val RoundTrip = ToEntropy(Actual)
        try {
            check(RoundTrip.contentEquals(ZeroEntropy)) { "BIP39 round-trip failed" }
        } finally { RoundTrip.fill(0) }
    }

    fun Display(Word: String): String = Word.replaceFirstChar { it.titlecase(Locale.ROOT) }
}
