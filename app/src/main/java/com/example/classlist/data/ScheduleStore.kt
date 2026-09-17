package com.example.classlist.data

import android.content.Context
import androidx.core.content.edit
import com.example.classlist.model.Course
import com.example.classlist.model.CourseSegment
import org.json.JSONArray
import org.json.JSONObject

class ScheduleStore(context: Context) {
    private val preferences = context.getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)

    fun save(courses: List<Course>) {
        val array = JSONArray()
        courses.forEach { course ->
            array.put(
                JSONObject().apply {
                    put("day", course.dayOfWeek)
                    put("start", course.startSection)
                    put("end", course.endSection)
                    put("title", course.title)
                    put("teacher", course.teacher)
                    put("location", course.location)
                    put("weekText", course.weekText)
                    put("details", JSONArray(course.details))
                    put("weeks", course.activeWeeks?.let { JSONArray(it.sorted()) })
                    put("period", course.period)
                    put("code", course.code)
                    put(
                        "segments",
                        JSONArray().apply {
                            course.segments.forEach { segment ->
                                put(
                                    JSONObject().apply {
                                        put("weekText", segment.weekText)
                                        put("weeks", segment.activeWeeks?.let { JSONArray(it.sorted()) })
                                        put("teacher", segment.teacher)
                                        put("location", segment.location)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
        preferences.edit {
            putString(KEY_COURSES, array.toString())
            putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        }
    }

    fun load(): List<Course> {
        val storedCourses = runCatching {
            val array = JSONArray(preferences.getString(KEY_COURSES, "[]"))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        Course(
                            dayOfWeek = item.getInt("day"),
                            startSection = item.getInt("start"),
                            endSection = item.getInt("end"),
                            title = item.getString("title"),
                            teacher = item.optNullableString("teacher"),
                            location = item.optNullableString("location"),
                            weekText = item.optNullableString("weekText"),
                            details = item.optJSONArray("details").toStringList(),
                            activeWeeks = item.optJSONArray("weeks")?.toIntSet(),
                            period = item.optNullableString("period"),
                            code = item.optNullableString("code"),
                            segments = item.optJSONArray("segments").toSegments(),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

        val validCourses = storedCourses.filter { course -> course.title.any(Char::isLetter) }
        if (storedCourses.isNotEmpty() && validCourses.isEmpty()) {
            preferences.edit {
                remove(KEY_COURSES)
                remove(KEY_UPDATED_AT)
            }
        }
        return validCourses
    }

    fun updatedAt(): Long = preferences.getLong(KEY_UPDATED_AT, 0L)

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { index -> optString(index) }.filter(String::isNotBlank)
    }

    private fun JSONArray.toIntSet(): Set<Int> =
        buildSet { for (index in 0 until length()) add(optInt(index)) }

    private fun JSONArray?.toSegments(): List<CourseSegment> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val weekText = item.optString("weekText").takeIf(String::isNotBlank) ?: continue
                add(
                    CourseSegment(
                        weekText = weekText,
                        activeWeeks = item.optJSONArray("weeks")?.toIntSet(),
                        teacher = item.optNullableString("teacher"),
                        location = item.optNullableString("location"),
                    ),
                )
            }
        }
    }

    private companion object {
        const val KEY_COURSES = "courses"
        const val KEY_UPDATED_AT = "updated_at"
    }
}
