package com.example.classlist.data

data class ClassTimeRange(
    val startMinute: Int,
    val endMinute: Int,
) {
    val displayText: String
        get() = "${startMinute.asClockText()}-${endMinute.asClockText()}"

    private fun Int.asClockText(): String = "%02d:%02d".format(this / 60, this % 60)
}

object ClassTimeResolver {
    private enum class CampusSchedule {
        XUEYUAN_ROAD,
        SHAHE_BUILDINGS_ONE_TWO,
        SHAHE_OTHER,
    }

    fun resolveRange(startSection: Int, endSection: Int, location: String?): ClassTimeRange? {
        if (startSection !in 1..12 || endSection !in startSection..12) return null
        val schedule = location.toCampusSchedule()
        val start = sectionTime(startSection, schedule)?.first ?: return null
        val end = sectionTime(endSection, schedule)?.second ?: return null
        return ClassTimeRange(startMinute = start, endMinute = end)
    }

    fun formatRange(startSection: Int, endSection: Int, location: String?): String? =
        resolveRange(startSection, endSection, location)?.displayText

    private fun sectionTime(section: Int, schedule: CampusSchedule?): Pair<Int, Int>? = when (section) {
        1 -> minutes(8, 0) to minutes(8, 45)
        2 -> minutes(8, 50) to minutes(9, 35)
        3 -> when (schedule) {
            CampusSchedule.XUEYUAN_ROAD -> minutes(9, 55) to minutes(10, 40)
            CampusSchedule.SHAHE_BUILDINGS_ONE_TWO -> minutes(10, 10) to minutes(10, 55)
            CampusSchedule.SHAHE_OTHER -> minutes(9, 50) to minutes(10, 35)
            null -> null
        }
        4 -> when (schedule) {
            CampusSchedule.XUEYUAN_ROAD -> minutes(10, 45) to minutes(11, 30)
            CampusSchedule.SHAHE_BUILDINGS_ONE_TWO -> minutes(11, 0) to minutes(11, 45)
            CampusSchedule.SHAHE_OTHER -> minutes(10, 40) to minutes(11, 25)
            null -> null
        }
        5 -> minutes(13, 30) to minutes(14, 15)
        6 -> minutes(14, 20) to minutes(15, 5)
        7 -> minutes(15, 20) to minutes(16, 5)
        8 -> minutes(16, 10) to minutes(16, 55)
        9 -> minutes(17, 0) to minutes(17, 45)
        10 -> minutes(19, 0) to minutes(19, 45)
        11 -> minutes(19, 50) to minutes(20, 35)
        12 -> minutes(20, 40) to minutes(21, 25)
        else -> null
    }

    private fun minutes(hour: Int, minute: Int): Int = hour * 60 + minute

    private fun String?.toCampusSchedule(): CampusSchedule? {
        val compact = this?.replace(Regex("\\s+"), "")?.takeIf(String::isNotBlank) ?: return null
        return when {
            compact.contains("学院路") -> CampusSchedule.XUEYUAN_ROAD
            Regex("沙教(?:1|2|一|二)(?:号楼)?").containsMatchIn(compact) ->
                CampusSchedule.SHAHE_BUILDINGS_ONE_TWO
            compact.contains("沙教") || compact.contains("沙河") -> CampusSchedule.SHAHE_OTHER
            else -> null
        }
    }
}
