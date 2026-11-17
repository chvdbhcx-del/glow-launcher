package app.lawnchair.glow

import app.lawnchair.allapps.GlowCategoryRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlowCategoryRulesTest {

    @Test
    fun matchesWholeTokensOnly() {
        assertEquals(GlowCategoryRules.SOCIAL, GlowCategoryRules.keywordCategory("org.telegram.messenger", "Telegram"))
        // "gram" inside "program" and "tv" inside another word must not match
        assertNull(GlowCategoryRules.keywordCategory("com.example.programmer", "Programmer"))
        assertNull(GlowCategoryRules.keywordCategory("com.tvzvezda.app", "Zvezda"))
        assertEquals(GlowCategoryRules.ENTERTAINMENT, GlowCategoryRules.keywordCategory("com.example.tv", "Smart TV"))
    }

    @Test
    fun financeIsItsOwnGroup() {
        assertEquals(GlowCategoryRules.FINANCE, GlowCategoryRules.keywordCategory("ru.sberbank.online", "СберБанк"))
        assertEquals(GlowCategoryRules.PRODUCTIVITY, GlowCategoryRules.keywordCategory("com.google.android.gm", "Gmail"))
    }

    @Test
    fun sectionLetters() {
        assertEquals("T", GlowCategoryRules.sectionOf("telegram"))
        assertEquals("Я", GlowCategoryRules.sectionOf("яндекс"))
        assertEquals("#", GlowCategoryRules.sectionOf("2GIS"))
        assertEquals("#", GlowCategoryRules.sectionOf(null))
    }

    @Test
    fun tabletsGetMoreColumns() {
        assertEquals(2, GlowCategoryRules.tileColumns(390f))
        assertEquals(3, GlowCategoryRules.tileColumns(600f))
        assertEquals(4, GlowCategoryRules.tileColumns(1200f))
    }
}
