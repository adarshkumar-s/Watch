package com.adarshkumar.omnitrix.protocol

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the audit rule: pre-audit assumptions stay preserved-as-data, always
 * labeled UNVERIFIED, and never get silently deleted or promoted.
 */
class UnverifiedLegacyCatalogTest {

    @Test
    fun `legacy uuids from the pre-audit code are catalogued`() {
        assertTrue(UnverifiedLegacyCatalog.isLegacyUuid("16186f00-0000-1000-8000-00807f9b34fb"))
        assertTrue(UnverifiedLegacyCatalog.isLegacyUuid("16186f01-0000-1000-8000-00807f9b34fb"))
        assertTrue(UnverifiedLegacyCatalog.isLegacyUuid("16186f02-0000-1000-8000-00807f9b34fb"))
    }

    @Test
    fun `legacy labels always carry the UNVERIFIED marker`() {
        for (lu in UnverifiedLegacyCatalog.uuidAssumptions) {
            val note = UnverifiedLegacyCatalog.legacyNote(lu.uuid)
            assertNotNull(note)
            assertTrue(note!!.contains("UNVERIFIED"))
        }
    }

    @Test
    fun `discovered real services are not flagged as legacy`() {
        assertNull(UnverifiedLegacyCatalog.legacyNote("0000feea-0000-1000-8000-00805f9b34fb"))
        assertNull(UnverifiedLegacyCatalog.legacyNote("0000180a-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun `guessed packets are archived but marked never-validated`() {
        assertTrue(UnverifiedLegacyCatalog.packetAssumptions.isNotEmpty())
        UnverifiedLegacyCatalog.packetAssumptions.forEach {
            assertTrue(it.note.contains("never") || it.note.contains("archived"))
        }
    }
}
