package io.github.lenkr0.verba.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureIntentsTest {
    private val own = "io.github.lenkr0.verba"

    @Test
    fun callingAppWins() {
        assertEquals("com.android.chrome", CaptureIntents.sourceApp(own, "com.android.chrome", "org.koreader.launcher"))
    }

    @Test
    fun relaysAndVerbaItselfAreSkipped() {
        assertEquals("org.koreader.launcher", CaptureIntents.sourceApp(own, null, "org.koreader.launcher"))
        assertEquals("app.mihon", CaptureIntents.sourceApp(own, "com.android.intentresolver", "app.mihon"))
        assertNull(CaptureIntents.sourceApp(own, "android", "$own.debug"))
        assertNull(CaptureIntents.sourceApp(own, null, ""))
    }
}
