/*
 *  Copyright (c) 2022~2026 chr_56
 */

package player.phonograph.foundation.mediastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NaturalCompareTest {

    @Test
    fun numbersAreComparedAsNumbers() {
        assertEquals(listOf("1", "2", "10"), listOf("10", "2", "1").sortedWith(Comparator { a, b -> naturalCompare(a, b) }))
        assertEquals(listOf("a2", "a10", "a100"), listOf("a100", "a2", "a10").sortedWith(Comparator { a, b -> naturalCompare(a, b) }))
    }

    @Test
    fun caseIsIgnored() {
        assertEquals(0, naturalCompare("Song", "song"))
    }

    @Test
    fun hugeNumbersDoNotOverflow() {
        val huge = "9".repeat(40)
        assertTrue(naturalCompare("a$huge", "a1") > 0)
        assertTrue(naturalCompare("a1", "a$huge") < 0)
    }

    @Test
    fun leadingZerosAreEqual() {
        assertEquals(0, naturalCompare("track01", "track1"))
    }

    @Test
    fun digitsComeBeforeLetters() {
        assertTrue(naturalCompare("1", "a") < 0)
        assertTrue(naturalCompare("a", "1") > 0)
    }

    @Test
    fun arabicIndicDigitsDoNotCrash() {
        assertTrue(naturalCompare("أغنية ٢", "أغنية ١٠") < 0)
    }

    @Test
    fun nullsAreLastAndEqualToEachOther() {
        val comparator = naturalStringComparator<String?> { it }
        assertEquals(0, comparator.compare(null, null))
        assertTrue(comparator.compare(null, "a") > 0)
        assertTrue(comparator.compare("a", null) < 0)
    }

    /**
     * Regression of "Comparison method violates its general contract!":
     * many songs without album/artist (null) must be sortable.
     */
    @Test
    fun sortingManyNullsDoesNotThrow() {
        val random = Random(42)
        val values: List<String?> = List(5000) {
            when (random.nextInt(4)) {
                0    -> null
                1    -> "Album ${random.nextInt(50)}"
                2    -> "album${random.nextInt(1000)}x"
                else -> "${random.nextInt(100)}"
            }
        }
        val comparator = naturalStringComparator<String?> { it }
        values.sortedWith(comparator)
        values.sortedWith(comparator.reversed())
    }

    @Test
    fun comparatorIsAntisymmetricAndTransitive() {
        val random = Random(7)
        val alphabet = "aAbB019 _-!"
        val samples = List(120) { String(CharArray(random.nextInt(0, 7)) { alphabet[random.nextInt(alphabet.length)] }) }
        for (a in samples) for (b in samples) {
            assertEquals("antisymmetry: '$a' '$b'", Integer.signum(naturalCompare(a, b)), -Integer.signum(naturalCompare(b, a)))
        }
        for (a in samples) for (b in samples) for (c in samples) {
            if (naturalCompare(a, b) <= 0 && naturalCompare(b, c) <= 0) {
                assertTrue("transitivity: '$a' '$b' '$c'", naturalCompare(a, c) <= 0)
            }
        }
    }
}
