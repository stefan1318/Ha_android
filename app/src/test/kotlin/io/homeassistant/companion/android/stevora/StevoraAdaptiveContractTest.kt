package io.homeassistant.companion.android.stevora

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StevoraAdaptiveContractTest {
    @Test
    fun sharedFixturePreservesScopeAndTypedActions() {
        val fixturePath = System.getenv("STEVORA_ADAPTIVE_FIXTURE")
        assumeNotNull(fixturePath)
        val fixture = File(requireNotNull(fixturePath)).readText()
        val actions = requireNotNull(parseStevoraAdaptiveActions(fixture, "fixture-home-a", "bg"))

        val mode = actions.getValue("climate.fixture").first { it.id == "hvac_mode" }
        assertEquals("registry:air", mode.capabilityId)
        assertEquals("Режим", mode.label)
        assertTrue(mode.confirmationRequired)
        val field = mode.parameters.getValue("hvac_mode")
        assertEquals("heat", field.parse("heat"))
        assertNull(field.parse("unsupported"))
        assertTrue(actions.getValue("sensor.fixture").isEmpty())

        assertNull(parseStevoraAdaptiveActions(fixture, "fixture-home-b", "bg"))
        val future = JSONObject(fixture).put("schema_version", 2).toString()
        assertNull(parseStevoraAdaptiveActions(future, "fixture-home-a", "bg"))
    }
}
