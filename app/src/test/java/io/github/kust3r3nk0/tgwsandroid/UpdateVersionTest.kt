package io.github.kust3r3nk0.tgwsandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test fun newerPrereleaseDetected() {
        val current = numericVersion("0.3.5-alpha")
        val next = numericVersion("v0.3.6-alpha-debug")
        assertNotNull(current)
        assertNotNull(next)
        assertTrue(next!! > current!!)
    }

    @Test fun stableSupersedesMatchingPrerelease() {
        assertTrue(numericVersion("v1.0.0")!! > numericVersion("v1.0.0-alpha")!!)
    }

    @Test fun olderOrEqualIsNotUpdate() {
        assertTrue(numericVersion("v0.3.4-alpha-debug")!! < numericVersion("0.3.5-alpha")!!)
        assertEquals(numericVersion("0.3.5-alpha"), numericVersion("v0.3.5-alpha-debug"))
    }

    @Test fun malformedAndUnsupportedTagsRejected() {
        assertNull(numericVersion("latest"))
        assertNull(numericVersion("v0.3"))
        assertNull(numericVersion("1.2.3.4"))
    }
}
