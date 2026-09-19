package com.adarshkumar.omnitrix.diag

import com.adarshkumar.omnitrix.protocol.Direction
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticLogTest {

    @Before
    fun reset() = DiagnosticLog.clear()

    @Test
    fun `render includes timestamp direction and category`() {
        DiagnosticLog.log(Direction.PHONE_TO_WATCH, "GATT", "read request for 00002a26-…")
        val out = DiagnosticLog.render()
        assertTrue(out.contains("[PHONE → WATCH]"))
        assertTrue(out.contains("[GATT]"))
        assertTrue(out.contains("read request"))
    }

    @Test
    fun `directions stay distinct`() {
        DiagnosticLog.tx("T", "outgoing")
        DiagnosticLog.rx("T", "incoming")
        val lines = DiagnosticLog.render().lines()
        assertTrue(lines.any { it.contains("PHONE → WATCH") && it.contains("outgoing") })
        assertTrue(lines.any { it.contains("WATCH → PHONE") && it.contains("incoming") })
    }

    @Test
    fun `clear empties the log`() {
        DiagnosticLog.info("T", "x")
        DiagnosticLog.clear()
        assertTrue(DiagnosticLog.render().isEmpty())
    }
}
