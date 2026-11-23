package app.lawnchair.glow

import app.lawnchair.search.algorithms.GlowSearchVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowSearchVariantsTest {

    private val prefix: (String, String) -> Boolean = { q, title ->
        title.lowercase().split(' ').any { it.startsWith(q) }
    }

    @Test
    fun wrongKeyboardLayout() {
        assertEquals("ozon", GlowSearchVariants.swapLayout("щящт"))
        assertEquals("сбер", GlowSearchVariants.swapLayout("c,th"))
    }

    @Test
    fun transliteration() {
        assertEquals("telegram", GlowSearchVariants.toLatin("телеграм"))
        assertTrue(GlowSearchVariants.matches(GlowSearchVariants.variants("телеграм"), "Telegram", null, prefix))
        assertTrue(GlowSearchVariants.matches(GlowSearchVariants.variants("sber"), "Сбер", null, prefix))
    }

    @Test
    fun packageNameAndNoFalseHits() {
        assertTrue(GlowSearchVariants.matches(GlowSearchVariants.variants("whats"), "Мессенджер", "com.whatsapp", prefix))
        assertFalse(GlowSearchVariants.matches(GlowSearchVariants.variants("zzz"), "Telegram", "org.telegram.messenger", prefix))
    }
}
