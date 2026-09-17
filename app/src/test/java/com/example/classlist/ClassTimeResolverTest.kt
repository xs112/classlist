package com.example.classlist

import com.example.classlist.data.ClassTimeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassTimeResolverTest {
    @Test
    fun resolvesCommonAfternoonAndEveningSections() {
        assertEquals("13:30-15:05", ClassTimeResolver.formatRange(5, 6, null))
        assertEquals("15:20-16:55", ClassTimeResolver.formatRange(7, 8, "沙教4-405"))
        assertEquals("19:00-21:25", ClassTimeResolver.formatRange(10, 12, null))
    }

    @Test
    fun resolvesThirdAndFourthSectionsByBuilding() {
        assertEquals("10:10-11:45", ClassTimeResolver.formatRange(3, 4, "沙教1-404"))
        assertEquals("10:10-11:45", ClassTimeResolver.formatRange(3, 4, "沙教二号楼201"))
        assertEquals("09:50-11:25", ClassTimeResolver.formatRange(3, 4, "沙教4-405"))
        assertEquals("09:55-11:30", ClassTimeResolver.formatRange(3, 4, "学院路教学楼101"))
    }

    @Test
    fun avoidsGuessingLocationDependentTime() {
        assertNull(ClassTimeResolver.formatRange(3, 4, null))
        assertNull(ClassTimeResolver.formatRange(0, 2, "沙教1-404"))
        assertNull(ClassTimeResolver.formatRange(4, 3, "沙教1-404"))
    }
}
