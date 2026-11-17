package app.lawnchair.glow

import app.lawnchair.update.GlowUpdateLogic
import app.lawnchair.update.GlowUpdateLogic.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowUpdateLogicTest {

    private fun release(tag: String, sha: Boolean = true, draft: Boolean = false) =
        Release(tag, "https://github.com/x/$tag.apk", if (sha) "https://github.com/x/$tag.apk.sha256" else null, 100, draft)

    @Test
    fun onlyHttpsGithubHostsAreAllowed() {
        assertTrue(GlowUpdateLogic.isAllowedUrl("https", "github.com"))
        assertTrue(GlowUpdateLogic.isAllowedUrl("https", "release-assets.githubusercontent.com"))
        assertFalse(GlowUpdateLogic.isAllowedUrl("http", "github.com"))
        assertFalse(GlowUpdateLogic.isAllowedUrl("https", "evil.example.com"))
        assertFalse(GlowUpdateLogic.isAllowedUrl("https", "github.com.evil.example.com"))
        assertFalse(GlowUpdateLogic.isAllowedUrl("https", null))
    }

    @Test
    fun preReleasesSortBeforeTheirRelease() {
        assertTrue(GlowUpdateLogic.compareVersions("v1.1.0", "v1.1.0-beta1") > 0)
        assertTrue(GlowUpdateLogic.compareVersions("v1.1.0-rc1", "v1.1.0-beta3") > 0)
        assertTrue(GlowUpdateLogic.compareVersions("v1.1.0-beta2", "v1.1.0-beta1") > 0)
        assertTrue(GlowUpdateLogic.compareVersions("v1.0.10", "v1.0.9") > 0)
        assertEquals(0, GlowUpdateLogic.compareVersions("v1.2", "1.2.0"))
    }

    @Test
    fun testChannelFollowsHighestTestNumber() {
        val list = listOf(release("v1.0.0-test17"), release("v1.0.0-test19"), release("v1.0.0"))
        assertEquals("v1.0.0-test19", GlowUpdateLogic.pickNewer(list, "v1.0.0-test18")?.tag)
        assertNull(GlowUpdateLogic.pickNewer(list, "v1.0.0-test19"))
    }

    @Test
    fun releaseChannelIgnoresTestBuilds() {
        val list = listOf(release("v1.0.0-test30"), release("v1.0.1"), release("v1.1.0-beta1"))
        assertEquals("v1.0.1", GlowUpdateLogic.pickNewer(list, "v1.0.0")?.tag)
        assertNull(GlowUpdateLogic.pickNewer(list, "v1.0.1"))
        assertEquals("v1.1.0-beta1", GlowUpdateLogic.pickNewer(list, "v1.0.1-beta1")?.tag)
    }

    @Test
    fun releasesWithoutChecksumOrDraftsAreSkipped() {
        val list = listOf(release("v1.0.0-test20", sha = false), release("v1.0.0-test21", draft = true))
        assertNull(GlowUpdateLogic.pickNewer(list, "v1.0.0-test18"))
    }

    @Test
    fun parsesSha256SumFiles() {
        val hash = "a".repeat(64)
        assertEquals(hash, GlowUpdateLogic.parseSha256("$hash  Glow-Launcher-v1.apk\n"))
        assertEquals(hash, GlowUpdateLogic.parseSha256(hash.uppercase()))
        assertNull(GlowUpdateLogic.parseSha256("not a hash"))
    }
}
