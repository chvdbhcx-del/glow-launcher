/*
 * Copyright 2026 Glow Launcher contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.update

import java.util.Locale

/**
 * Pure (Android-free) rules of the Glow updater, kept apart so they can be unit tested.
 */
object GlowUpdateLogic {

    /** Hosts the updater may talk to. GitHub serves release assets from these. */
    val ALLOWED_HOSTS = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    data class Release(
        val tag: String,
        val apkUrl: String,
        val sha256Url: String?,
        val apkSize: Long,
        val draft: Boolean = false,
    )

    fun isAllowedUrl(protocol: String?, host: String?): Boolean =
        protocol.equals("https", ignoreCase = true) &&
            host != null && host.lowercase(Locale.ROOT) in ALLOWED_HOSTS

    /** `v1.0.0-test15` -> 15, anything else -> null. */
    fun testNumber(tag: String): Int? =
        Regex("-test(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Semantic-ish comparison of release tags: `v1.2.0` > `v1.2.0-rc2` > `v1.2.0-beta1` > `v1.1.9`.
     * A pre-release suffix sorts before the plain version it belongs to.
     */
    fun compareVersions(a: String, b: String): Int {
        val (coreA, preA) = split(a)
        val (coreB, preB) = split(b)
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val c = coreA.getOrElse(i) { 0 }.compareTo(coreB.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return when {
            preA == null && preB == null -> 0
            preA == null -> 1
            preB == null -> -1
            else -> comparePre(preA, preB)
        }
    }

    private fun split(tag: String): Pair<List<Int>, String?> {
        val v = tag.trim().removePrefix("v").removePrefix("V")
        val dash = v.indexOf('-')
        val core = if (dash >= 0) v.substring(0, dash) else v
        val pre = if (dash >= 0) v.substring(dash + 1).lowercase(Locale.ROOT) else null
        return core.split('.').map { it.toIntOrNull() ?: 0 } to pre
    }

    private fun comparePre(a: String, b: String): Int {
        val rank = listOf("alpha", "beta", "rc")
        fun parts(p: String): Pair<Int, Int> {
            val name = p.takeWhile { it.isLetter() }
            val num = p.dropWhile { it.isLetter() }.filter { it.isDigit() }.toIntOrNull() ?: 0
            return rank.indexOf(name) to num
        }
        val (ra, na) = parts(a)
        val (rb, nb) = parts(b)
        return if (ra != rb) ra.compareTo(rb) else na.compareTo(nb)
    }

    /**
     * Picks the release this build should update to, or null.
     * Test builds (or local builds without a tag) follow the test channel, signed releases
     * follow signed releases. Releases without an APK or a checksum are ignored.
     */
    fun pickNewer(releases: List<Release>, ownTag: String): Release? {
        val usable = releases.filter { !it.draft && it.sha256Url != null }
        val ownTest = testNumber(ownTag)
        return if (ownTag.isEmpty() || ownTest != null) {
            usable.filter { testNumber(it.tag) != null }
                .maxByOrNull { testNumber(it.tag)!! }
                ?.takeIf { ownTest == null || testNumber(it.tag)!! > ownTest }
        } else {
            // Stable builds only move to stable releases; a beta build may also take newer betas.
            val ownIsPre = ownTag.contains('-')
            usable.filter { testNumber(it.tag) == null && (ownIsPre || !it.tag.contains('-')) }
                .maxWithOrNull { x, y -> compareVersions(x.tag, y.tag) }
                ?.takeIf { compareVersions(it.tag, ownTag) > 0 }
        }
    }

    /** Reads the hash out of a `sha256sum`-style file ("<64 hex>  name.apk"). */
    fun parseSha256(text: String): String? =
        Regex("\\b([0-9a-fA-F]{64})\\b").find(text)?.groupValues?.get(1)?.lowercase(Locale.ROOT)

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
