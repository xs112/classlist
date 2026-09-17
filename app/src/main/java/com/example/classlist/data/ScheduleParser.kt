package com.example.classlist.data

import com.example.classlist.model.Course
import com.example.classlist.model.CourseSegment
import com.example.classlist.model.GridCell
import org.json.JSONArray
import org.json.JSONObject

object ScheduleParser {
    private val weekExpressionPattern = Regex("(?<![A-Za-z0-9])([\\d\\s,，、~至-]+)\\s*周")
    private val weekRangePattern = Regex("^(\\d{1,2})\\s*[-~至]\\s*(\\d{1,2})$")
    private val courseCodePattern = Regex("^[A-Za-z0-9_-]{4,}$")
    private val courseSeparatorPattern = Regex("[;；](?=\\s*[A-Za-z0-9_-]{4,}\\s*\\|)")
    private val teacherPattern = Regex("(?:任课教师|教师|老师|任课)\\s*[:：]\\s*([^\\]］\\n\\r,，}｝]+)")
    private val locationPattern = Regex("(?:上课地点|地点|教室)\\s*[:：]\\s*([^\\]］\\n\\r,，}｝]+)")

    fun fromExtractionJson(json: String): List<Course> {
        val root = JSONObject(json)
        require(root.optString("status") == "ok") { root.optString("message", "未找到课表") }
        val sourceCells = root.getJSONArray("cells")
        val cells = buildList {
            for (index in 0 until sourceCells.length()) {
                val cell = sourceCells.getJSONObject(index)
                add(
                    GridCell(
                        row = cell.getInt("row"),
                        column = cell.getInt("column"),
                        rowSpan = cell.optInt("rowSpan", 1).coerceAtLeast(1),
                        columnSpan = cell.optInt("columnSpan", 1).coerceAtLeast(1),
                        text = cell.optString("text"),
                    ),
                )
            }
        }
        return parse(cells)
    }

    fun parse(cells: List<GridCell>): List<Course> {
        if (cells.isEmpty()) return emptyList()

        val headerCandidates = cells.mapNotNull { cell ->
            dayFromHeader(cell.text)?.let { cell to it }
        }
        val headerRow = headerCandidates
            .groupingBy { it.first.row }
            .eachCount()
            .maxByOrNull { it.value }
            ?.takeIf { it.value >= 2 }
            ?.key
            ?: return emptyList()

        val dayColumns = headerCandidates
            .filter { it.first.row == headerRow }
            .associate { it.first.column to it.second }
        val firstDayColumn = dayColumns.keys.minOrNull() ?: return emptyList()

        return cells.asSequence()
            .filter { it.row > headerRow && it.text.isMeaningfulCourseText() }
            .flatMap { cell ->
                val coveredDays = dayColumns.filterKeys { column ->
                    column in cell.column until (cell.column + cell.columnSpan)
                }.values
                coveredDays.asSequence().flatMap { day ->
                    cell.toCourses(day, cells, firstDayColumn, headerRow).asSequence()
                }
            }
            .distinctBy { "${it.dayOfWeek}:${it.startSection}:${it.code.orEmpty()}:${it.title}:${it.weekText.orEmpty()}" }
            .sortedWith(compareBy(Course::dayOfWeek, Course::startSection, Course::title))
            .toList()
    }

    private fun GridCell.toCourses(
        day: Int,
        cells: List<GridCell>,
        firstDayColumn: Int,
        headerRow: Int,
    ): List<Course> {
        val sectionLabel = cells.firstOrNull { candidate ->
            candidate.row == row &&
                candidate.column < firstDayColumn &&
                candidate.text.parseSectionRange() != null
        }?.text
        val sectionRange = sectionLabel?.parseSectionRange()
        val fallbackStart = (row - headerRow).coerceAtLeast(1)
        val start = sectionRange?.first ?: fallbackStart
        val end = maxOf(sectionRange?.last ?: start, start + rowSpan - 1)
        val period = cells.firstOrNull { candidate ->
            candidate.column < firstDayColumn &&
                row in candidate.row until (candidate.row + candidate.rowSpan) &&
                candidate.text.trim() in setOf("上午", "下午", "晚上")
        }?.text?.trim()

        return text.split(courseSeparatorPattern)
            .mapNotNull { entry ->
                parseCourseEntry(
                    text = entry.trim(),
                    day = day,
                    start = start,
                    end = end,
                    period = period,
                )
            }
    }

    private fun parseCourseEntry(
        text: String,
        day: Int,
        start: Int,
        end: Int,
        period: String?,
    ): Course? {
        val lines = text
            .lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (lines.isEmpty() || !text.isMeaningfulCourseText()) return null

        val parsedSegments = parseSegments(text)
        val weekText = parsedSegments.joinToString("、") { it.weekText }.ifBlank { null }
        val teacherLine = lines.firstOrNull { it.contains("教师") || it.contains("老师") || it.contains("任课") }
        val locationLine = lines.firstOrNull {
            it.contains("教室") || it.contains("地点") || it.contains("校区") ||
                Regex("(?:楼|室|馆)").containsMatchIn(it)
        }
        val teachers = teacherPattern.findAll(text).map { it.groupValues[1].trim() }.filter(String::isNotBlank).distinct().toList()
        val locations = locationPattern.findAll(text).map { it.groupValues[1].trim() }.filter(String::isNotBlank).distinct().toList()
        val courseHead = text.substringBefore('｛').substringBefore('{').trim()
        val pipeIndex = courseHead.indexOf('|')
        val code = if (pipeIndex > 0) {
            courseHead.substring(0, pipeIndex).trim().takeIf(courseCodePattern::matches)
        } else {
            null
        }
        val structuredTitle = courseHead.substring(pipeIndex + 1).trim().takeIf { pipeIndex >= 0 && it.isNotBlank() }
        val metadata = listOfNotNull(teacherLine, locationLine).toSet() +
            lines.filter { it.contains('周') && it.any(Char::isDigit) }
        val title = structuredTitle
            ?: lines.firstOrNull { it !in metadata && !it.matches(Regex("第?\\d+(?:[-~至]\\d+)?节")) }
            ?: lines.first()
        val teacher = teachers.joinToString("、").ifBlank {
            teacherLine?.stripLabel("教师", "老师", "任课教师", "任课").orEmpty()
        }.ifBlank { null }
        val location = locations.joinToString("、").ifBlank {
            locationLine?.stripLabel("教室", "地点", "上课地点").orEmpty()
        }.ifBlank { null }
        val segments = if (parsedSegments.size == 1) {
            parsedSegments.map { segment ->
                segment.copy(
                    teacher = segment.teacher ?: teacher,
                    location = segment.location ?: location,
                )
            }
        } else {
            parsedSegments
        }

        return Course(
            dayOfWeek = day,
            startSection = start,
            endSection = end.coerceAtLeast(start),
            title = title,
            teacher = teacher,
            location = location,
            weekText = weekText,
            details = if (structuredTitle != null) emptyList() else lines.filterNot { it == title || it in metadata },
            activeWeeks = parseWeeks(weekText),
            period = period,
            code = code,
            segments = segments,
        )
    }

    private fun parseSegments(text: String): List<CourseSegment> {
        val matches = weekExpressionPattern.findAll(text).toList()
        return matches.mapIndexed { index, match ->
            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val segmentBody = text.substring(match.range.last + 1, nextStart)
            val normalizedWeeks = match.groupValues[1]
                .replace(Regex("\\s+"), "")
                .trim(',', '，', '、') + "周"
            val parity = when {
                segmentBody.contains("单周") -> "单周"
                segmentBody.contains("双周") -> "双周"
                else -> null
            }
            val segmentWeekText = if (parity == null) normalizedWeeks else "$normalizedWeeks($parity)"
            CourseSegment(
                weekText = segmentWeekText,
                activeWeeks = parseWeeks(segmentWeekText),
                teacher = teacherPattern.find(segmentBody)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank),
                location = locationPattern.find(segmentBody)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank),
            )
        }
    }

    private fun parseWeeks(text: String?): Set<Int>? {
        if (text.isNullOrBlank()) return null
        val result = sortedSetOf<Int>()

        weekExpressionPattern.findAll(text).forEach { expression ->
            expression.groupValues[1].split(Regex("[,，、]")).forEach { rawToken ->
                val token = rawToken.trim().removePrefix("第")
                val range = weekRangePattern.matchEntire(token)
                if (range != null) {
                    val start = range.groupValues[1].toInt()
                    val end = range.groupValues[2].toInt()
                    if (start <= end) result.addAll(start..end)
                } else {
                    token.toIntOrNull()?.let(result::add)
                }
            }
        }
        if (result.isEmpty() && (text.contains("单周") || text.contains("双周"))) {
            result += 1..25
        }
        if (text.contains("单周")) result.removeAll { it % 2 == 0 }
        if (text.contains("双周")) result.removeAll { it % 2 != 0 }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun String.parseSectionRange(): IntRange? {
        val compact = replace(" ", "")
        if (!contains('节') && !compact.matches(Regex("第?\\d{1,2}(?:[-~至]\\d{1,2})?"))) return null
        val match = Regex("第?(\\d{1,2})(?:[-~至](\\d{1,2}))?").find(compact) ?: return null
        val start = match.groupValues[1].toInt()
        val end = match.groupValues[2].toIntOrNull() ?: start
        return if (start in 1..30 && end in start..30) start..end else null
    }

    private fun dayFromHeader(text: String): Int? {
        val value = text.replace(Regex("\\s+"), "")
        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        return names.indexOfFirst { day ->
            value == day || value.contains("周$day") || value.contains("星期$day") || value.contains("礼拜$day") ||
                (day == "日" && (value.contains("星期天") || value.contains("周天")))
        }.takeIf { it >= 0 }?.plus(1)
    }

    private fun String.isMeaningfulCourseText(): Boolean {
        val value = trim()
        return value.isNotEmpty() &&
            value.any(Char::isLetter) &&
            value !in setOf("-", "--", "无", "暂无", "上午", "下午", "晚上") &&
            dayFromHeader(value) == null
    }

    private fun String.stripLabel(vararg labels: String): String =
        labels.fold(this) { value, label -> value.replace(Regex("^$label\\s*[:：]?\\s*"), "") }.trim()
}
