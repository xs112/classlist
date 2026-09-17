package com.example.classlist

import com.example.classlist.data.ClassTimeRange
import com.example.classlist.data.CourseFocusState
import com.example.classlist.data.ScheduleFocusResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleFocusResolverTest {
    private val ranges = listOf(
        ClassTimeRange(8 * 60, 9 * 60 + 35),
        ClassTimeRange(13 * 60 + 30, 15 * 60 + 5),
        ClassTimeRange(15 * 60 + 20, 16 * 60 + 55),
    )

    @Test
    fun locatesCurrentOrNextCourse() {
        assertEquals(0, ScheduleFocusResolver.find(ranges, 7 * 60)?.index)
        assertEquals(CourseFocusState.NEXT, ScheduleFocusResolver.find(ranges, 7 * 60)?.state)
        assertEquals(1, ScheduleFocusResolver.find(ranges, 14 * 60)?.index)
        assertEquals(CourseFocusState.CURRENT, ScheduleFocusResolver.find(ranges, 14 * 60)?.state)
        assertEquals(2, ScheduleFocusResolver.find(ranges, 15 * 60 + 10)?.index)
        assertEquals(CourseFocusState.NEXT, ScheduleFocusResolver.find(ranges, 15 * 60 + 10)?.state)
    }

    @Test
    fun keepsLastCourseVisibleAfterClassesEnd() {
        val focus = ScheduleFocusResolver.find(ranges, 22 * 60)
        assertEquals(2, focus?.index)
        assertNull(focus?.state)
        assertNull(ScheduleFocusResolver.find(emptyList(), 12 * 60))
    }
}
