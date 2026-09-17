package com.example.classlist.model

data class Course(
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val title: String,
    val teacher: String?,
    val location: String?,
    val weekText: String?,
    val details: List<String>,
    val activeWeeks: Set<Int>?,
    val period: String? = null,
    val code: String? = null,
    val segments: List<CourseSegment> = emptyList(),
) {
    val identityKey: String
        get() = code?.takeIf(String::isNotBlank) ?: title

    fun occursInWeek(week: Int): Boolean = week == 0 || activeWeeks == null || week in activeWeeks
}

data class CourseSegment(
    val weekText: String,
    val activeWeeks: Set<Int>?,
    val teacher: String?,
    val location: String?,
) {
    fun occursInWeek(week: Int): Boolean = week == 0 || activeWeeks == null || week in activeWeeks
}

data class GridCell(
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val columnSpan: Int,
    val text: String,
)
