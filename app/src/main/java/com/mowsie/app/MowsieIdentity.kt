package com.mowsie.app

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Mowsie development identity scheme, version 1.
 *
 *   BIP39 128-bit entropy -> 12 English words -> BIP39 512-bit seed (empty BIP39 passphrase)
 *   -> SLIP-0010 Ed25519 master -> hardened m/0'/StashIndex' -> raw 32-byte public key.
 *
 * NOTE: m/0'/StashIndex' is Mowsie's provisional v1 path, NOT a registered BIP44 coin path.
 * Neither this class nor the QR is a production Salt wallet/address format.
 * Private key seed is never returned or stored; derive it only when signing is eventually needed.
 */
object MowsieIdentity {
    data class PublicIdentity(val PublicKeyHex: String, val Path: String)

    fun Derive(Entropy: ByteArray, StashIndex: Int = 0): PublicIdentity {
        require(Entropy.size == 16) { "Expected 128-bit BIP39 entropy" }
        require(StashIndex >= 0) { "Stash index must be nonnegative" }

        val Words = GenesisWords.FromEntropy(Entropy)
        val Phrase = Normalizer.normalize(Words.joinToString(" "), Normalizer.Form.NFKD)
        // BIP39's optional passphrase is intentionally empty here. The local Mowsie Secret
        // is only an app unlock Secret; it MUST NOT affect the derived public address.
        val Seed = Bip39Seed(Phrase)
        try {
            var Node = HmacSha512("ed25519 seed".toByteArray(StandardCharsets.US_ASCII), Seed)
            try {
                for (Index in intArrayOf(0, StashIndex)) {
                    val Child = HardenedChild(Node, Index)
                    Node.fill(0)
                    Node = Child
                }
                val PrivateSeed = Node.copyOfRange(0, 32)
                return try {
                    val PublicBytes = Ed25519PrivateKeyParameters(PrivateSeed, 0)
                        .generatePublicKey().encoded
                    try {
                        PublicIdentity(PublicBytes.ToHex(), "m/0'/${StashIndex}'")
                    } finally {
                        PublicBytes.fill(0)
                    }
                } finally {
                    PrivateSeed.fill(0)
                }
            } finally {
                Node.fill(0)
            }
        } finally {
            Seed.fill(0)
        }
    }

    // SLIP-0010 Ed25519 hardened child: HMAC(chainCode, 0x00 || parentKey || ser32(i | 0x80000000))
    private fun HardenedChild(Node: ByteArray, Index: Int): ByteArray {
        val Data = ByteArray(37)
        System.arraycopy(Node, 0, Data, 1, 32)
        val Child = Index or Int.MIN_VALUE
        Data[33] = (Child ushr 24).toByte()
        Data[34] = (Child ushr 16).toByte()
        Data[35] = (Child ushr 8).toByte()
        Data[36] = Child.toByte()
        val Chain = Node.copyOfRange(32, 64)
        return try { HmacSha512(Chain, Data) }
        finally { Chain.fill(0); Data.fill(0) }
    }

    private fun Bip39Seed(Phrase: String): ByteArray {
        val Password = Phrase.toByteArray(StandardCharsets.UTF_8)
        val Salt = "mnemonic".toByteArray(StandardCharsets.UTF_8)
        // PBKDF2-HMAC-SHA512, c=2048, dkLen=64 (BIP39). This is NOT the removed
        // 300,000-iteration local app-Secret KDF. Required for standard BIP39 recovery.
        val Data = ByteArray(Salt.size + 4)
        System.arraycopy(Salt, 0, Data, 0, Salt.size)
        Data[Data.lastIndex] = 1
        val Result = ByteArray(64)
        val Mac = Mac.getInstance("HmacSHA512")
        try {
            Mac.init(SecretKeySpec(Password, "HmacSHA512"))
            var U = Mac.doFinal(Data)
            try {
                System.arraycopy(U, 0, Result, 0, Result.size)
                repeat(2047) {
                    val Next = Mac.doFinal(U)
                    U.fill(0)
                    U = Next
                    for (I in Result.indices) Result[I] = (Result[I].toInt() xor U[I].toInt()).toByte()
                }
            } finally { U.fill(0) }
            return Result
        } finally {
            Password.fill(0)
            Data.fill(0)
            Salt.fill(0)
        }
    }

    private fun HmacSha512(Key: ByteArray, Data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA512").apply {
            init(SecretKeySpec(Key, "HmacSHA512"))
        }.doFinal(Data)

    private fun ByteArray.ToHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** Deterministic interoperability check from the canonical BIP39 zero-entropy phrase. */
    fun SelfTest() {
        val Zero = ByteArray(16)
        val Result = Derive(Zero)
        val Expected = "e671f3047fd0727ec41a37fa1c36df0fd19e85a17bfa0d3bc3b48e44629dfce0"
        check(MessageDigest.isEqual(Result.PublicKeyHex.toByteArray(), Expected.toByteArray())) {
            "Mowsie public key derivation test failed"
        }
    }
}
