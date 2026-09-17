package com.example.classlist.data

enum class CourseFocusState {
    CURRENT,
    NEXT,
}

data class ScheduleFocus(
    val index: Int,
    val state: CourseFocusState?,
)

object ScheduleFocusResolver {
    fun find(ranges: List<ClassTimeRange?>, nowMinute: Int): ScheduleFocus? {
        val upcoming = ranges.withIndex().firstOrNull { (_, range) ->
            range != null && nowMinute <= range.endMinute
        }
        if (upcoming != null) {
            val range = requireNotNull(upcoming.value)
            return ScheduleFocus(
                index = upcoming.index,
                state = if (nowMinute >= range.startMinute) CourseFocusState.CURRENT else CourseFocusState.NEXT,
            )
        }
        return ranges.indexOfLast { it != null }
            .takeIf { it >= 0 }
            ?.let { ScheduleFocus(index = it, state = null) }
    }
}
