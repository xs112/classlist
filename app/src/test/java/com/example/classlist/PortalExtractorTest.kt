package com.example.classlist

import com.example.classlist.web.PortalExtractor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalExtractorTest {
    @Test
    fun doesNotGuessAClassScheduleEndpoint() {
        assertFalse(PortalExtractor.script.contains("ClassSchema.aspx"))
    }

    @Test
    fun requiresPersonalScheduleNavigationAndScheduleStructure() {
        assertTrue(PortalExtractor.script.contains("学期课表信息查询"))
        assertTrue(PortalExtractor.script.contains(PortalExtractor.PERSONAL_SCHEDULE_PATH.lowercase()))
        assertTrue(PortalExtractor.script.contains("hasExplicitScheduleContext"))
        assertTrue(PortalExtractor.script.contains("hasScheduleStructure"))
    }
}
