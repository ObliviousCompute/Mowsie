package com.mowsie.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * V4 is a DEVICE-BOUND, Android-Keystore-encrypted Genesis with a fast local Secret gate.
 * No password KDF is used by V4. This is NOT resistant to all device-compromise threats.
 *
 * The V3 PBKDF2 reader is retained SOLELY to migrate an existing V3 Genesis safely.
 * That slower operation happens only on the first successful unlock after an upgrade.
 */
class GenesisStore(private val Context: Context) {
    private val Random = SecureRandom()
    private val V4File = File(Context.noBackupFilesDir, "mowsie_genesis_v4.bin")
    private val V3File = File(Context.noBackupFilesDir, "mowsie_genesis_v3.bin")
    private val KeyAlias = "MowsieGenesisDeviceKeyV3" // retain existing device key for migration
    private val V4Magic = "MOWSIE-GENESIS-V4"
    private val V3Magic = "MOWSIE-GENESIS-V3"

    fun Exists(): Boolean = V4File.exists() || V3File.exists()

    @Synchronized fun GenerateAndSave(Secret: CharArray) {
        require(Secret.isNotEmpty()) { "Enter at least one character" }
        check(!Exists()) { "Genesis already exists. Never silently replace it." }
        GenesisWords.SelfTest()
        val Entropy = ByteArray(16).also(Random::nextBytes)
        try { WriteV4(Entropy, Secret, allowReplace = false) }
        finally { Entropy.fill(0) }
    }

    /** Returns the same 16-byte entropy on each successful unlock. Caller must wipe it. */
    @Synchronized fun Unlock(Secret: CharArray): ByteArray {
        require(Secret.isNotEmpty()) { "Enter your Secret" }
        if (V4File.exists()) return ReadV4(V4File, Secret)
        check(V3File.exists()) { "No Genesis found. Restore using your recovery words." }

        // One-time migration. Never delete the V3 file until V4 has been verified.
        val Entropy = ReadLegacyV3(Secret)
        try {
            WriteV4(Entropy, Secret, allowReplace = false)
            val Verification = ReadV4(V4File, Secret)
            try { check(MessageDigest.isEqual(Verification, Entropy)) { "Migration verification failed" } }
            finally { Verification.fill(0) }
            // Best effort removal. V4 always takes precedence after migration.
            V3File.delete()
            return Entropy.copyOf()
        } finally { Entropy.fill(0) }
    }

    /** Explicit, authenticated Genesis replacement after BIP39 verification and user confirmation. */
    @Synchronized fun ReplaceEntropy(NewEntropy: ByteArray, Secret: CharArray) {
        require(NewEntropy.size == 16)
        val Previous = Unlock(Secret) // verifies existing Secret, migrates old V3 if necessary
        try {
            if (MessageDigest.isEqual(Previous, NewEntropy)) return
            WriteV4(NewEntropy, Secret, allowReplace = true)
        } finally { Previous.fill(0) }
    }

    /** Change the local Secret without modifying BIP39 entropy or any future derived address. */
    @Synchronized fun ChangeSecret(OldSecret: CharArray, NewSecret: CharArray) {
        require(NewSecret.isNotEmpty()) { "Enter at least one character" }
        val Entropy = Unlock(OldSecret)
        try { WriteV4(Entropy, NewSecret, allowReplace = true) }
        finally { Entropy.fill(0) }
    }

    private fun WriteV4(Entropy: ByteArray, Secret: CharArray, allowReplace: Boolean) {
        require(Entropy.size == 16)
        require(Secret.isNotEmpty())
        if (!allowReplace) check(!V4File.exists()) { "Genesis already exists" }

        val Salt = ByteArray(16).also(Random::nextBytes)
        val Digest = SecretDigest(Secret, Salt)
        val Plain = ByteArray(64)
        System.arraycopy(Entropy, 0, Plain, 0, 16)
        System.arraycopy(Salt, 0, Plain, 16, 16)
        System.arraycopy(Digest, 0, Plain, 32, 32)
        Digest.fill(0)

        val Cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val Ciphertext: ByteArray
        val Iv: ByteArray
        try {
            Cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, DeviceKey(true))
            Ciphertext = Cipher.doFinal(Plain)
            Iv = Cipher.iv
        } finally { Plain.fill(0) }

        val Record = ByteArrayOutputStream().also { Buffer ->
            DataOutputStream(Buffer).use { Stream ->
                Stream.writeUTF(V4Magic)
                Stream.write(Iv)
                Stream.writeInt(Ciphertext.size)
                Stream.write(Ciphertext)
            }
        }.toByteArray()
        val Temporary = File(Context.noBackupFilesDir, "mowsie_genesis_v4.tmp")
        try {
            Temporary.outputStream().use { Stream ->
                Stream.write(Record)
                Stream.flush()
                (Stream as java.io.FileOutputStream).fd.sync()
            }
            val Verification = ReadV4(Temporary, Secret)
            try { check(MessageDigest.isEqual(Verification, Entropy)) { "Genesis verification failed" } }
            finally { Verification.fill(0) }
            // POSIX atomic replacement in the same directory, even if destination exists.
            Os.rename(Temporary.absolutePath, V4File.absolutePath)
        } finally {
            Temporary.delete()
            Ciphertext.fill(0)
            Record.fill(0)
        }
    }

    private fun ReadV4(FilePath: File, Secret: CharArray): ByteArray {
        val Raw = FilePath.readBytes()
        require(Raw.size in 50..512) { "Unexpected V4 record length" }
        return DataInputStream(ByteArrayInputStream(Raw)).use { Input ->
            require(Input.readUTF() == V4Magic) { "Unsupported Genesis format" }
            val Iv = ByteArray(12).also { Input.readFully(it) }
            val Size = Input.readInt()
            require(Size in 16..256) { "Invalid record" }
            val Encrypted = ByteArray(Size).also { Input.readFully(it) }
            require(Input.available() == 0) { "Trailing record data" }
            val Cipher = Cipher.getInstance("AES/GCM/NoPadding")
            Cipher.init(javax.crypto.Cipher.DECRYPT_MODE, DeviceKey(create = false), GCMParameterSpec(128, Iv))
            val Plain = Cipher.doFinal(Encrypted)
            try {
                check(Plain.size == 64) { "Bad Genesis data" }
                val Salt = Plain.copyOfRange(16, 32)
                val Expected = SecretDigest(Secret, Salt)
                try {
                    check(MessageDigest.isEqual(Expected, Plain.copyOfRange(32, 64))) {
                        "Wrong Secret"
                    }
                } finally { Expected.fill(0) }
                Plain.copyOfRange(0, 16)
            } finally { Plain.fill(0) }
        }
    }

    private fun SecretDigest(Secret: CharArray, Salt: ByteArray): ByteArray {
        // A fast verifier encrypted *inside* the Android Keystore AES-GCM envelope.
        // Intentionally not a password KDF. This is a CASUAL-ACCESS GATE, not a hardened vault.
        val SecretBytes = String(Secret).toByteArray(StandardCharsets.UTF_8)
        return try {
            MessageDigest.getInstance("SHA-256").apply {
                update("MowsieV4Secret".toByteArray(StandardCharsets.US_ASCII))
                update(0.toByte())
                update(Salt)
                update(SecretBytes)
            }.digest()
        } finally { SecretBytes.fill(0) }
    }

    private fun DeviceKey(create: Boolean): SecretKey {
        val Store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val Existing = Store.getKey(KeyAlias, null) as? SecretKey
        if (Existing != null) return Existing
        check(create) { "Device key missing. Restore from your recovery words." }
        val Generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        Generator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return Generator.generateKey()
    }

    /** Only backward compatibility with Genesis V3. Runs once and can be removed after migration. */
    private fun ReadLegacyV3(Secret: CharArray): ByteArray {
        val Raw = V3File.readBytes()
        require(Raw.size < 4096) { "Invalid V3 record size" }
        return DataInputStream(ByteArrayInputStream(Raw)).use { Input ->
            require(Input.readUTF() == V3Magic) { "Unexpected V3 format" }
            val Salt = ByteArray(16).also { Input.readFully(it) }
            val PasswordIv = ByteArray(12).also { Input.readFully(it) }
            val DeviceIv = ByteArray(12).also { Input.readFully(it) }
            val Size = Input.readInt()
            require(Size in 16..1024) { "Invalid V3 ciphertext" }
            val Outer = ByteArray(Size).also { Input.readFully(it) }
            require(Input.available() == 0) { "Trailing V3 data" }
            val Inner = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, DeviceKey(create = false), GCMParameterSpec(128, DeviceIv))
                doFinal(Outer)
            }
            try {
                val KeySpec = PBEKeySpec(Secret, Salt, 300_000, 256)
                val PasswordKeyBytes = try {
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                        .generateSecret(KeySpec).encoded
                } finally { KeySpec.clearPassword() }
                try {
                    val Key = SecretKeySpec(PasswordKeyBytes, "AES")
                    val Entropy = Cipher.getInstance("AES/GCM/NoPadding").run {
                        init(Cipher.DECRYPT_MODE, Key, GCMParameterSpec(128, PasswordIv))
                        doFinal(Inner)
                    }
                    require(Entropy.size == 16) { "Invalid V3 entropy" }
                    Entropy
                } finally { PasswordKeyBytes.fill(0) }
            } finally { Inner.fill(0) }
        }
    }
}
