package com.example.classlist.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object AcademicCalendarResolver {
    private data class TeachingTerm(
        val firstMonday: LocalDate,
        val weekCount: Int,
    )

    private val terms = listOf(
        TeachingTerm(LocalDate.of(2026, 8, 31), 20),
        TeachingTerm(LocalDate.of(2027, 3, 1), 19),
    )

    fun teachingWeek(date: LocalDate): Int? = terms.firstNotNullOfOrNull { term ->
        val days = ChronoUnit.DAYS.between(term.firstMonday, date)
        if (days in 0 until (term.weekCount * 7L)) (days / 7L).toInt() + 1 else null
    }
}
