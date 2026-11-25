package com.fredsystems.brilliantboard

import com.fredsystems.brilliantboard.data.repository.VersionCompare
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test
    fun detectsNewerReleases() {
        assertTrue(VersionCompare.isNewer("v1.0.1", "1.0.0"))
        assertTrue(VersionCompare.isNewer("v2.0.0", "1.9.9"))
        assertTrue(VersionCompare.isNewer("v1.1.0", "1.0.9"))
    }

    @Test
    fun ignoresOlderAndEqualReleases() {
        assertFalse(VersionCompare.isNewer("v1.0.0", "1.0.1"))
        assertFalse(VersionCompare.isNewer("1.0.0", "1.0.0"))
        assertFalse(VersionCompare.isNewer("v1.0", "1.0.0"))
    }
}
