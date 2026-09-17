package com.example.classlist

import com.example.classlist.data.ScheduleParser
import com.example.classlist.model.GridCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleParserTest {
    @Test
    fun parsesSectionsMetadataAndWeekRange() {
        val cells = headers() + listOf(
            GridCell(1, 0, 1, 1, "1-2节"),
            GridCell(1, 1, 1, 1, "高等数值分析\n教师：张老师\n地点：教学楼A201\n1-8周(双周)"),
            GridCell(2, 0, 1, 1, "3-4节"),
            GridCell(2, 3, 1, 1, "学术英语\n李老师\n9-16周"),
        )

        val courses = ScheduleParser.parse(cells)

        assertEquals(2, courses.size)
        with(courses.first()) {
            assertEquals(1, dayOfWeek)
            assertEquals(1, startSection)
            assertEquals(2, endSection)
            assertEquals("高等数值分析", title)
            assertEquals("张老师", teacher)
            assertEquals("教学楼A201", location)
            assertEquals(setOf(2, 4, 6, 8), activeWeeks)
        }
    }

    @Test
    fun expandsAColumnSpanAcrossCoveredDays() {
        val cells = headers() + listOf(
            GridCell(1, 0, 1, 1, "5-6节"),
            GridCell(1, 2, 1, 2, "科研实践\n1,3,5周"),
        )

        val courses = ScheduleParser.parse(cells)

        assertEquals(listOf(2, 3), courses.map { it.dayOfWeek })
        assertTrue(courses.all { it.startSection == 5 && it.endSection == 6 })
        assertTrue(courses.all { it.occursInWeek(3) })
        assertTrue(courses.none { it.occursInWeek(4) })
    }

    @Test
    fun rejectsTablesWithoutWeekdayHeaders() {
        val courses = ScheduleParser.parse(
            listOf(
                GridCell(0, 0, 1, 1, "课程"),
                GridCell(0, 1, 1, 1, "成绩"),
                GridCell(1, 0, 1, 1, "机器学习"),
                GridCell(1, 1, 1, 1, "90"),
            ),
        )

        assertTrue(courses.isEmpty())
    }

    @Test
    fun rejectsMonthCalendarDatesAsCourses() {
        val cells = buildList {
            add(GridCell(0, 0, 1, 1, "星期一"))
            add(GridCell(0, 1, 1, 1, "星期二"))
            add(GridCell(0, 2, 1, 1, "星期三"))
            add(GridCell(0, 3, 1, 1, "星期四"))
            add(GridCell(0, 4, 1, 1, "星期五"))
            add(GridCell(0, 5, 1, 1, "星期六"))
            add(GridCell(0, 6, 1, 1, "星期日"))
            for (row in 1..6) {
                for (column in 0..6) {
                    add(GridCell(row, column, 1, 1, ((row - 1) * 7 + column + 1).toString()))
                }
            }
        }

        assertTrue(ScheduleParser.parse(cells).isEmpty())
    }

    @Test
    fun parsesMultipleWeekRangesAndPeriodFromRealScheduleShape() {
        val cells = listOf(
            GridCell(0, 0, 1, 1, "时间"),
            GridCell(0, 1, 1, 1, "节次"),
            GridCell(0, 2, 1, 1, "星期一"),
            GridCell(0, 3, 1, 1, "星期二"),
            GridCell(1, 0, 4, 1, "上午"),
            GridCell(1, 1, 1, 1, "1"),
            GridCell(1, 2, 2, 1, "分布式系统\n教师：张老师\n地点：教学楼A201\n1-6,8-16周"),
            GridCell(2, 1, 1, 1, "2"),
        )

        val course = ScheduleParser.parse(cells).single()

        assertEquals("上午", course.period)
        assertEquals(1, course.startSection)
        assertEquals(2, course.endSection)
        assertEquals((1..6).toSet() + (8..16).toSet(), course.activeWeeks)
    }

    @Test
    fun parsesCompactPortalCourseMetadata() {
        val cells = headers() + listOf(
            GridCell(1, 0, 1, 1, "3"),
            GridCell(
                1,
                1,
                1,
                1,
                "XS24004001Z|现代机械设计学术基础与前沿（可靠性设计、优化设计）1班｛" +
                    "3、6-8、10周[教师:商德勇,地点:沙教1-404]、" +
                    "11-15周[教师:李秀明,地点:沙教1-402]｝",
            ),
        )

        val course = ScheduleParser.parse(cells).single()

        assertEquals("XS24004001Z", course.code)
        assertEquals("现代机械设计学术基础与前沿（可靠性设计、优化设计）1班", course.title)
        assertEquals("商德勇、李秀明", course.teacher)
        assertEquals("沙教1-404、沙教1-402", course.location)
        assertEquals("3、6-8、10周、11-15周", course.weekText)
        assertEquals(setOf(3, 6, 7, 8, 10, 11, 12, 13, 14, 15), course.activeWeeks)
        assertEquals(2, course.segments.size)
        with(course.segments[0]) {
            assertEquals("3、6-8、10周", weekText)
            assertEquals(setOf(3, 6, 7, 8, 10), activeWeeks)
            assertEquals("商德勇", teacher)
            assertEquals("沙教1-404", location)
            assertTrue(occursInWeek(3))
            assertFalse(occursInWeek(11))
        }
        with(course.segments[1]) {
            assertEquals("11-15周", weekText)
            assertEquals((11..15).toSet(), activeWeeks)
            assertEquals("李秀明", teacher)
            assertEquals("沙教1-402", location)
            assertTrue(occursInWeek(11))
            assertFalse(occursInWeek(3))
        }
    }

    @Test
    fun splitsMultipleCoursesStoredInOneCell() {
        val cells = headers() + listOf(
            GridCell(1, 0, 1, 1, "5"),
            GridCell(
                1,
                1,
                2,
                1,
                "TS24009001G|自然辩证法概论3班（机电学硕+智能专硕+地测专硕2）｛12-16周[教师:教师甲,地点:教四-405]｝；" +
                    "XS24004001Z|现代机械设计学术基础与前沿1班｛3-4、6-10周[教师:教师乙,地点:教一-404]｝",
            ),
        )

        val courses = ScheduleParser.parse(cells)

        assertEquals(2, courses.size)
        assertTrue(courses.all { it.dayOfWeek == 1 && it.startSection == 5 && it.endSection == 6 })
        with(courses.first { it.code == "TS24009001G" }) {
            assertEquals("自然辩证法概论3班（机电学硕+智能专硕+地测专硕2）", title)
            assertEquals("12-16周", weekText)
            assertEquals((12..16).toSet(), activeWeeks)
            assertEquals("教师甲", teacher)
            assertEquals("教四-405", location)
        }
        with(courses.first { it.code == "XS24004001Z" }) {
            assertEquals("现代机械设计学术基础与前沿1班", title)
            assertEquals("3-4、6-10周", weekText)
            assertEquals(setOf(3, 4, 6, 7, 8, 9, 10), activeWeeks)
            assertEquals("教师乙", teacher)
            assertEquals("教一-404", location)
        }
    }

    private fun headers(): List<GridCell> = listOf(
        GridCell(0, 0, 1, 1, "节次"),
        GridCell(0, 1, 1, 1, "星期一"),
        GridCell(0, 2, 1, 1, "星期二"),
        GridCell(0, 3, 1, 1, "星期三"),
        GridCell(0, 4, 1, 1, "星期四"),
        GridCell(0, 5, 1, 1, "星期五"),
    )
}
