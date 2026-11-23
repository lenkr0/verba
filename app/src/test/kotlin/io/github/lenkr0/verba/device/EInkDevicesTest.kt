package io.github.lenkr0.verba.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EInkDevicesTest {
    @Test
    fun recognizesBoox() {
        assertTrue(EInkDevices.matches("ONYX", "Onyx", "Go7"))
        assertTrue(EInkDevices.matches("unknown", "BOOX", "Go Color 7"))
    }

    @Test
    fun ignoresPhonesAndMissingValues() {
        assertFalse(EInkDevices.matches("Xiaomi", "POCO", "24069PC21G"))
        assertFalse(EInkDevices.matches(null, null, null))
        assertFalse(EInkDevices.matches("Google", "google", "sdk_gphone64_x86_64"))
    }
}
