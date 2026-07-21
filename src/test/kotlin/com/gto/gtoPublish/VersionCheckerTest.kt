package com.gto.gtoPublish

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCheckerTest {

    @Test
    fun `accepts release and branch version formats`() {
        listOf(
            "1.2.3",
            "1.2.3-alpha",
            "1.2.3-beta",
            "1.2.3-release",
            "1.2.3-feature-login",
            "1.2.3-dev2"
        ).forEach(VersionChecker::checkVersionFormat)
    }

    @Test
    fun `rejects suffix characters outside lowercase letters digits and hyphens`() {
        listOf(
            "1.2.3-Feature",
            "1.2.3-feature_login",
            "1.2.3-"
        ).forEach { version ->
            assertFailsWith<GradleException> {
                VersionChecker.checkVersionFormat(version)
            }
        }
    }

    @Test
    fun `classifies non-release suffixes as branch versions`() {
        assertTrue(VersionChecker.isBranchVersion("1.2.3-feature-login"))
        assertTrue(VersionChecker.isBranchVersion("1.2.3-dev2"))

        assertFalse(VersionChecker.isBranchVersion("1.2.3"))
        assertFalse(VersionChecker.isBranchVersion("1.2.3-alpha"))
        assertFalse(VersionChecker.isBranchVersion("1.2.3-beta"))
        assertFalse(VersionChecker.isBranchVersion("1.2.3-release"))
    }

    @Test
    fun `compares semantic plugin versions`() {
        assertTrue(VersionChecker.compareVersions("1.0.24", "1.0.23") > 0)
        assertTrue(VersionChecker.compareVersions("1.0.23", "1.0.24") < 0)
        assertEquals(0, VersionChecker.compareVersions("1.0.23", "1.0.23"))
        assertTrue(VersionChecker.compareVersions("1.0.0", "1.0.0-beta") > 0)
        assertTrue(VersionChecker.compareVersions("1.0.0-alpha", "1.0.0-beta") < 0)
    }

    @Test
    fun `keeps release type parsing unchanged`() {
        assertEquals("release", VersionChecker.parseReleaseType("1.2.3"))
        assertEquals("alpha", VersionChecker.parseReleaseType("1.2.3-alpha"))
        assertEquals("beta", VersionChecker.parseReleaseType("1.2.3-beta"))
        assertEquals("release", VersionChecker.parseReleaseType("1.2.3-release"))
    }
}
