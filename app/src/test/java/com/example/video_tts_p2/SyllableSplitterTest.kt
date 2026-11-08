package com.example.video_tts_p2

import com.example.video_tts_p2.audio.SyllableSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyllableSplitterTest {

    @Test
    fun testWordSyllableSplitting() {
        val condign = SyllableSplitter.splitWord("condign")
        assertEquals(listOf("con", "dign"), condign)

        val dictionary = SyllableSplitter.splitWord("dictionary")
        assertEquals(listOf("dic", "tion", "ar", "y"), dictionary)

        val shortWord = SyllableSplitter.splitWord("cat")
        assertEquals(listOf("cat"), shortWord)
    }

    @Test
    fun testIpaSyllableSplitting() {
        val ipaParts = SyllableSplitter.splitIpa("/kənˈdaɪn/")
        assertTrue(ipaParts.isNotEmpty())
    }

    @Test
    fun testPhoneticSyllablePronunciation() {
        val condignPhonetic = SyllableSplitter.getPhoneticSyllables("condign")
        assertEquals(listOf("kuhn", "dahyn"), condignPhonetic.map { it.phonetic })

        val customWithIpa = SyllableSplitter.getPhoneticSyllables("condign", "kənˈdaɪn")
        assertEquals(listOf("kuhn", "dahyn"), customWithIpa.map { it.phonetic })
    }
}
