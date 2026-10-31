package com.mowsie.app

import org.junit.Assert.*
import org.junit.Test

class ScanProtocolTest {
    private val Key = "0123456789abcdef".repeat(4)
    private val Payload = "MOWSIE PUBLIC KEY V1\nAlgorithm: Ed25519\n" +
        "Derivation: m/0'/0'\nStash: Metatron\nPublic Key: $Key"

    @Test fun acceptsMowsiePublicQr() {
        val Identity = ScanProtocol.Recognize(Payload)
        assertNotNull(Identity)
        assertEquals("Metatron", Identity!!.Name)
        assertEquals("0123…CDEF", Identity.ShortKey)
    }

    @Test fun rejectsWrongAlgorithm() {
        assertNull(ScanProtocol.Recognize(Payload.replace("Ed25519", "RSA")))
    }

    @Test fun rejectsMalformedPublicKey() {
        assertNull(ScanProtocol.Recognize(Payload.replace(Key, "not a key")))
    }

    @Test fun rejectsUnrelatedQr() {
        assertNull(ScanProtocol.Recognize("https://example.org"))
    }
}
