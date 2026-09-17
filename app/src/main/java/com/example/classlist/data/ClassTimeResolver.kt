package com.example.classlist.data

object ClassTimeResolver {
    private enum class CampusSchedule {
        XUEYUAN_ROAD,
        SHAHE_BUILDINGS_ONE_TWO,
        SHAHE_OTHER,
    }

    fun formatRange(startSection: Int, endSection: Int, location: String?): String? {
        if (startSection !in 1..12 || endSection !in startSection..12) return null
        val schedule = location.toCampusSchedule()
        val start = sectionTime(startSection, schedule)?.first ?: return null
        val end = sectionTime(endSection, schedule)?.second ?: return null
        return "$start-$end"
    }

    private fun sectionTime(section: Int, schedule: CampusSchedule?): Pair<String, String>? = when (section) {
        1 -> "08:00" to "08:45"
        2 -> "08:50" to "09:35"
        3 -> when (schedule) {
            CampusSchedule.XUEYUAN_ROAD -> "09:55" to "10:40"
            CampusSchedule.SHAHE_BUILDINGS_ONE_TWO -> "10:10" to "10:55"
            CampusSchedule.SHAHE_OTHER -> "09:50" to "10:35"
            null -> null
        }
        4 -> when (schedule) {
            CampusSchedule.XUEYUAN_ROAD -> "10:45" to "11:30"
            CampusSchedule.SHAHE_BUILDINGS_ONE_TWO -> "11:00" to "11:45"
            CampusSchedule.SHAHE_OTHER -> "10:40" to "11:25"
            null -> null
        }
        5 -> "13:30" to "14:15"
        6 -> "14:20" to "15:05"
        7 -> "15:20" to "16:05"
        8 -> "16:10" to "16:55"
        9 -> "17:00" to "17:45"
        10 -> "19:00" to "19:45"
        11 -> "19:50" to "20:35"
        12 -> "20:40" to "21:25"
        else -> null
    }

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
