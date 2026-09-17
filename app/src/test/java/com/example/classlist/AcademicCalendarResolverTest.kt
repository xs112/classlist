package com.example.classlist

import com.example.classlist.data.AcademicCalendarResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AcademicCalendarResolverTest {
    @Test
    fun resolvesAutumnTeachingWeeks() {
        assertNull(AcademicCalendarResolver.teachingWeek(LocalDate.of(2026, 8, 30)))
        assertEquals(1, AcademicCalendarResolver.teachingWeek(LocalDate.of(2026, 8, 31)))
        assertEquals(1, AcademicCalendarResolver.teachingWeek(LocalDate.of(2026, 9, 6)))
        assertEquals(2, AcademicCalendarResolver.teachingWeek(LocalDate.of(2026, 9, 7)))
        assertEquals(3, AcademicCalendarResolver.teachingWeek(LocalDate.of(2026, 9, 17)))
        assertEquals(20, AcademicCalendarResolver.teachingWeek(LocalDate.of(2027, 1, 17)))
        assertNull(AcademicCalendarResolver.teachingWeek(LocalDate.of(2027, 1, 18)))
    }

    @Test
    fun resolvesSpringTeachingWeeksAndVacation() {
        assertNull(AcademicCalendarResolver.teachingWeek(LocalDate.of(2027, 2, 28)))
        assertEquals(1, AcademicCalendarResolver.teachingWeek(LocalDate.of(2027, 3, 1)))
        assertEquals(19, AcademicCalendarResolver.teachingWeek(LocalDate.of(2027, 7, 11)))
        assertNull(AcademicCalendarResolver.teachingWeek(LocalDate.of(2027, 7, 12)))
    }
}
