package com.example.classlist

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.classlist.data.AcademicCalendarResolver
import com.example.classlist.data.ClassTimeResolver
import com.example.classlist.data.ClassTimeRange
import com.example.classlist.data.CourseFocusState
import com.example.classlist.data.PortalSettings
import com.example.classlist.data.ScheduleFocus
import com.example.classlist.data.ScheduleFocusResolver
import com.example.classlist.data.ScheduleStore
import com.example.classlist.model.Course
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId

class MainActivity : AppCompatActivity() {
    private lateinit var store: ScheduleStore
    private var courses: List<Course> = emptyList()
    private var selectedDay = 1
    private var selectedWeek = 0
    private var focusCurrentSchedule = true
    private var lastLocatedDate: LocalDate? = null
    private var firstResumePending = true

    private lateinit var updatedText: TextView
    private lateinit var syncButton: MaterialButton
    private lateinit var weekControls: View
    private lateinit var weekButton: MaterialButton
    private lateinit var dayScroll: HorizontalScrollView
    private lateinit var dayTabs: LinearLayout
    private lateinit var emptyState: View
    private lateinit var scheduleScroll: ScrollView
    private lateinit var scheduleContent: LinearLayout

    private val portalResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            courses = store.load()
            locateNow()
            renderAll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PortalSettings.removeLegacySavedUrl(this)
        store = ScheduleStore(this)
        courses = store.load()
        locateNow()

        bindViews()
        createDayTabs()
        bindActions()
        renderAll()
    }

    override fun onResume() {
        super.onResume()
        if (firstResumePending) {
            firstResumePending = false
            return
        }
        if (::store.isInitialized && courses.isNotEmpty()) {
            locateNow()
            renderSchedule()
        }
    }

    private fun bindViews() {
        updatedText = findViewById(R.id.updatedText)
        syncButton = findViewById(R.id.syncButton)
        weekControls = findViewById(R.id.weekControls)
        weekButton = findViewById(R.id.weekButton)
        dayScroll = findViewById(R.id.dayScroll)
        dayTabs = findViewById(R.id.dayTabs)
        emptyState = findViewById(R.id.emptyState)
        scheduleScroll = findViewById(R.id.scheduleScroll)
        scheduleContent = findViewById(R.id.scheduleContent)
    }

    private fun bindActions() {
        findViewById<MaterialButton>(R.id.loginButton).setOnClickListener {
            openPortal()
        }
        syncButton.setOnClickListener { openPortal() }
        findViewById<MaterialButton>(R.id.previousWeekButton).setOnClickListener {
            selectedWeek = when (selectedWeek) {
                0, 1 -> 0
                else -> selectedWeek - 1
            }
            focusCurrentSchedule = false
            renderSchedule()
        }
        findViewById<MaterialButton>(R.id.nextWeekButton).setOnClickListener {
            selectedWeek = if (selectedWeek == 0) 1 else (selectedWeek + 1).coerceAtMost(25)
            focusCurrentSchedule = false
            renderSchedule()
        }
        weekButton.setOnClickListener {
            selectedWeek = 0
            focusCurrentSchedule = false
            renderSchedule()
        }
        findViewById<MaterialButton>(R.id.todayButton).setOnClickListener {
            locateNow()
            renderSchedule()
        }
    }

    private fun openPortal() {
        portalResult.launch(Intent(this, PortalActivity::class.java))
    }

    private fun createDayTabs() {
        val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        labels.forEachIndexed { index, label ->
            val day = index + 1
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                text = label
                isAllCaps = false
                minWidth = dp(64)
                minimumWidth = dp(64)
                cornerRadius = dp(8)
                insetTop = 0
                insetBottom = 0
                setOnClickListener {
                    selectedDay = day
                    focusCurrentSchedule = false
                    renderSchedule()
                }
            }
            dayTabs.addView(button, LinearLayout.LayoutParams(dp(64), dp(42)).apply {
                marginEnd = dp(8)
            })
        }
    }

    private fun renderAll() {
        val hasCourses = courses.isNotEmpty()
        emptyState.visibility = if (hasCourses) View.GONE else View.VISIBLE
        syncButton.visibility = if (hasCourses) View.VISIBLE else View.GONE
        weekControls.visibility = if (hasCourses) View.VISIBLE else View.GONE
        dayScroll.visibility = if (hasCourses) View.VISIBLE else View.GONE
        scheduleScroll.visibility = if (hasCourses) View.VISIBLE else View.GONE

        val updatedAt = store.updatedAt()
        updatedText.text = if (updatedAt == 0L) {
            getString(R.string.not_synced)
        } else {
            getString(R.string.updated_at, SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(updatedAt)))
        }
        if (hasCourses) renderSchedule()
    }

    private fun renderSchedule() {
        weekButton.text = if (selectedWeek == 0) getString(R.string.all_weeks) else getString(R.string.week_number, selectedWeek)
        updateDayTabStyles()
        scheduleContent.removeAllViews()

        val visibleCourses = courses.filter {
            it.dayOfWeek == selectedDay && it.occursInWeek(selectedWeek)
        }
        val uniqueCourseCount = visibleCourses.distinctBy(Course::identityKey).size
        scheduleContent.addView(TextView(this).apply {
            text = getString(R.string.day_course_count, dayName(selectedDay), uniqueCourseCount)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            textSize = 13f
            setPadding(dp(20), 0, dp(20), dp(12))
        })

        if (visibleCourses.isEmpty()) {
            scheduleContent.addView(TextView(this).apply {
                text = "本日无课"
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                textSize = 16f
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(180)))
            scheduleScroll.scrollTo(0, 0)
            focusCurrentSchedule = false
            return
        }
        val focus = currentFocus(visibleCourses)
        visibleCourses.forEachIndexed { index, course ->
            scheduleContent.addView(createCourseCard(course, focus?.takeIf { it.index == index }?.state))
        }
        if (focus != null && focusCurrentSchedule) {
            scheduleScroll.post {
                val target = scheduleContent.getChildAt(focus.index + 1)
                scheduleScroll.scrollTo(0, (target.top - dp(12)).coerceAtLeast(0))
            }
        } else {
            scheduleScroll.scrollTo(0, 0)
        }
        focusCurrentSchedule = false
    }

    private fun updateDayTabStyles() {
        val primary = ContextCompat.getColor(this, R.color.primary)
        val surface = ContextCompat.getColor(this, R.color.surface)
        val outline = ContextCompat.getColor(this, R.color.outline)
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)
        for (index in 0 until dayTabs.childCount) {
            val button = dayTabs.getChildAt(index) as MaterialButton
            val selected = index + 1 == selectedDay
            button.backgroundTintList = ColorStateList.valueOf(if (selected) primary else surface)
            button.setTextColor(if (selected) Color.WHITE else textPrimary)
            button.strokeColor = ColorStateList.valueOf(if (selected) primary else outline)
            if (selected) {
                dayScroll.post {
                    val targetX = button.left - (dayScroll.width - button.width) / 2
                    dayScroll.smoothScrollTo(targetX.coerceAtLeast(0), 0)
                }
            }
        }
    }

    private fun createCourseCard(course: Course, focusState: CourseFocusState?): View {
        val accentColors = intArrayOf(
            Color.rgb(23, 107, 69),
            Color.rgb(25, 95, 130),
            Color.rgb(176, 90, 43),
            Color.rgb(132, 61, 92),
            Color.rgb(109, 91, 35),
            Color.rgb(48, 101, 105),
            Color.rgb(90, 83, 138),
        )
        return MaterialCardView(this).apply {
            radius = dp(8).toFloat()
            cardElevation = 0f
            strokeWidth = dp(if (focusState == null) 1 else 2)
            strokeColor = ContextCompat.getColor(
                this@MainActivity,
                when (focusState) {
                    CourseFocusState.CURRENT -> R.color.secondary
                    CourseFocusState.NEXT -> R.color.primary
                    null -> R.color.outline
                },
            )
            setCardBackgroundColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    when (focusState) {
                        CourseFocusState.CURRENT -> R.color.surface_current
                        CourseFocusState.NEXT -> R.color.surface_next
                        null -> R.color.surface
                    },
                ),
            )
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                bottomMargin = dp(12)
            }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(View(this@MainActivity).apply {
                    setBackgroundColor(accentColors[(course.dayOfWeek - 1).coerceIn(0, 6)])
                }, LinearLayout.LayoutParams(dp(5), LinearLayout.LayoutParams.MATCH_PARENT))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(15), dp(16), dp(16))
                    addView(createCourseHeader(course, focusState))
                    addView(TextView(this@MainActivity).apply {
                        text = course.title
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                        textSize = 18f
                        setTypeface(typeface, Typeface.BOLD)
                        setLineSpacing(dp(2).toFloat(), 1f)
                        setPadding(0, dp(7), 0, dp(10))
                    })
                    val visibleSegments = course.segments.filter { it.occursInWeek(selectedWeek) }
                    val timeLocations = relevantLocations(course, visibleSegments.map { it.location })
                    val clockRanges = timeLocations
                        .mapNotNull { location ->
                            ClassTimeResolver.formatRange(course.startSection, course.endSection, location)
                        }
                        .distinct()
                    val section = getString(R.string.section_range, course.startSection, course.endSection)
                    if (clockRanges.size <= 1) {
                        addView(
                            infoRow(
                                getString(R.string.label_time),
                                listOfNotNull(dayName(course.dayOfWeek), clockRanges.singleOrNull()).joinToString(" · "),
                                prominent = true,
                            ),
                        )
                    }
                    addView(
                        infoRow(
                            getString(R.string.label_sections),
                            listOfNotNull(course.period, section).joinToString(" · "),
                        ),
                    )
                    if (visibleSegments.isNotEmpty()) {
                        visibleSegments.forEachIndexed { index, segment ->
                            val segmentTime = ClassTimeResolver.formatRange(
                                course.startSection,
                                course.endSection,
                                segment.location,
                            )
                            if (visibleSegments.size > 1) {
                                addView(segmentDivider(index + 1))
                            }
                            if (clockRanges.size > 1 && segmentTime != null) {
                                addView(
                                    infoRow(
                                        getString(R.string.label_time),
                                        "${dayName(course.dayOfWeek)} · $segmentTime",
                                        prominent = true,
                                    ),
                                )
                            }
                            addView(infoRow(getString(R.string.label_weeks), segment.weekText))
                            addView(
                                infoRow(
                                    getString(R.string.label_location),
                                    segment.location ?: getString(R.string.not_specified),
                                    prominent = true,
                                ),
                            )
                            addView(
                                infoRow(
                                    getString(R.string.label_teacher),
                                    segment.teacher ?: getString(R.string.not_specified),
                                ),
                            )
                        }
                    } else {
                        addView(infoRow(getString(R.string.label_weeks), course.weekText ?: getString(R.string.not_specified)))
                        addView(
                            infoRow(
                                getString(R.string.label_location),
                                course.location ?: getString(R.string.not_specified),
                                prominent = true,
                            ),
                        )
                        addView(infoRow(getString(R.string.label_teacher), course.teacher ?: getString(R.string.not_specified)))
                    }
                    course.code?.let { addView(infoRow(getString(R.string.label_code), it)) }
                    course.details.forEach { addView(infoRow(getString(R.string.label_detail), it)) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
    }

    private fun createCourseHeader(course: Course, focusState: CourseFocusState?): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.section_range, course.startSection, course.endSection)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.secondary))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val status = when (focusState) {
                CourseFocusState.CURRENT -> getString(R.string.current_class)
                CourseFocusState.NEXT -> getString(R.string.next_class)
                null -> null
            }
            if (status != null) {
                addView(TextView(this@MainActivity).apply {
                    text = status
                    setTextColor(
                        ContextCompat.getColor(
                            this@MainActivity,
                            if (focusState == CourseFocusState.CURRENT) R.color.secondary else R.color.primary,
                        ),
                    )
                    textSize = 11f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                    background = roundedBackground(
                        ContextCompat.getColor(
                            this@MainActivity,
                            if (focusState == CourseFocusState.CURRENT) R.color.badge_current else R.color.badge_next,
                        ),
                        dp(4).toFloat(),
                    )
                })
            }
        }

    private fun infoRow(label: String, value: String, prominent: Boolean = false): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(4), 0, dp(4))

            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_label))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
            }, LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT))

            addView(TextView(this@MainActivity).apply {
                text = value
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (prominent) R.color.text_primary else R.color.text_secondary,
                    ),
                )
                textSize = if (prominent) 14f else 13f
                if (prominent) setTypeface(typeface, Typeface.BOLD)
                setLineSpacing(dp(2).toFloat(), 1f)
                includeFontPadding = false
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun segmentDivider(number: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(9), 0, dp(3))
        addView(View(this@MainActivity).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.divider))
        }, LinearLayout.LayoutParams(0, dp(1), 1f))
        addView(TextView(this@MainActivity).apply {
            text = getString(R.string.teaching_segment, number)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_label))
            textSize = 11f
            setPadding(dp(8), 0, dp(8), 0)
        })
        addView(View(this@MainActivity).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.divider))
        }, LinearLayout.LayoutParams(0, dp(1), 1f))
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }

    private fun locateNow() {
        val now = ZonedDateTime.now(CHINA_ZONE)
        selectedDay = now.dayOfWeek.value
        selectedWeek = AcademicCalendarResolver.teachingWeek(now.toLocalDate()) ?: 0
        lastLocatedDate = now.toLocalDate()
        focusCurrentSchedule = true
    }

    private fun currentFocus(visibleCourses: List<Course>): ScheduleFocus? {
        val now = ZonedDateTime.now(CHINA_ZONE)
        if (!focusCurrentSchedule || now.toLocalDate() != lastLocatedDate || selectedDay != now.dayOfWeek.value) return null
        val currentWeek = AcademicCalendarResolver.teachingWeek(now.toLocalDate()) ?: return null
        if (selectedWeek != currentWeek) return null
        val ranges = visibleCourses.map(::courseTimeRange)
        return ScheduleFocusResolver.find(ranges, now.hour * 60 + now.minute)
    }

    private fun courseTimeRange(course: Course): ClassTimeRange? {
        val segmentLocations = course.segments
            .filter { it.occursInWeek(selectedWeek) }
            .map { it.location }
        val ranges = relevantLocations(course, segmentLocations).mapNotNull { location ->
            ClassTimeResolver.resolveRange(course.startSection, course.endSection, location)
        }
        if (ranges.isEmpty()) return null
        return ClassTimeRange(
            startMinute = ranges.minOf(ClassTimeRange::startMinute),
            endMinute = ranges.maxOf(ClassTimeRange::endMinute),
        )
    }

    private fun relevantLocations(course: Course, segmentLocations: List<String?>): List<String?> {
        val source = segmentLocations.ifEmpty { listOf(course.location) }
        val locations = source.flatMap { location -> location?.split('、') ?: emptyList() }
        return locations.ifEmpty { listOf(null) }
    }

    private fun dayName(day: Int): String = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[day - 1]

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
