package com.antigravity.equalizer

import com.antigravity.equalizer.data.model.Preset
import com.antigravity.equalizer.data.repository.PresetJsonSerializer
import org.junit.Assert.*
import org.junit.Test

class PresetJsonSerializerTest {

    @Test
    fun testExportAndImportPresetJson() {
        val original = Preset(
            id = "test_preset",
            name = "Test Rock",
            isCustom = true,
            preampGainDb = -2.5f,
            bands10Gain = listOf(3.0f, 2.0f, 1.0f, 0f, -1.0f, -0.5f, 1.5f, 3.0f, 4.0f, 4.5f),
            limiterEnabled = true,
            limiterThresholdDb = -0.3f
        )

        val jsonString = PresetJsonSerializer.exportPresetToJson(original)
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("Test Rock"))

        val parsed = PresetJsonSerializer.importPresetFromJson(jsonString)
        assertNotNull(parsed)
        assertEquals(original.id, parsed!!.id)
        assertEquals(original.name, parsed.name)
        assertEquals(original.preampGainDb, parsed.preampGainDb, 0.001f)
        assertEquals(original.bands10Gain.size, parsed.bands10Gain.size)
        assertEquals(original.bands10Gain[0], parsed.bands10Gain[0], 0.001f)
    }

    @Test
    fun testParseAutoEqText() {
        val autoEqText = "GraphicEQ: 31.25 4.5; 62.5 3.0; 125 1.5; 250 0; 500 -1.0; 1000 -0.5; 2000 1.5; 4000 3.0; 8000 4.0; 16000 5.0"
        val preset = PresetJsonSerializer.parseAutoEqText("Sony WH-1000XM5 AutoEQ", autoEqText)

        assertNotNull(preset)
        assertEquals("Sony WH-1000XM5 AutoEQ", preset!!.name)
        assertEquals(10, preset.bands10Gain.size)
        assertEquals(4.5f, preset.bands10Gain[0], 0.1f)
        assertEquals(5.0f, preset.bands10Gain[9], 0.1f)
    }
}
