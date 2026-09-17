package com.example.classlist

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.classlist.data.ClassTimeResolver
import com.example.classlist.data.PortalSettings
import com.example.classlist.data.ScheduleStore
import com.example.classlist.model.Course
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var store: ScheduleStore
    private var courses: List<Course> = emptyList()
    private var selectedDay = 1
    private var selectedWeek = 0

    private lateinit var updatedText: TextView
    private lateinit var syncButton: MaterialButton
    private lateinit var weekControls: View
    private lateinit var weekButton: MaterialButton
    private lateinit var dayScroll: View
    private lateinit var dayTabs: LinearLayout
    private lateinit var emptyState: View
    private lateinit var scheduleScroll: View
    private lateinit var scheduleContent: LinearLayout

    private val portalResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            courses = store.load()
            renderAll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PortalSettings.removeLegacySavedUrl(this)
        store = ScheduleStore(this)
        courses = store.load()
        selectedDay = calendarDayOfWeek()

        bindViews()
        createDayTabs()
        bindActions()
        renderAll()
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
            renderSchedule()
        }
        findViewById<MaterialButton>(R.id.nextWeekButton).setOnClickListener {
            selectedWeek = if (selectedWeek == 0) 1 else (selectedWeek + 1).coerceAtMost(25)
            renderSchedule()
        }
        weekButton.setOnClickListener {
            selectedWeek = 0
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
            return
        }
        visibleCourses.forEach { scheduleContent.addView(createCourseCard(it)) }
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
        }
    }

    private fun createCourseCard(course: Course): View {
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
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.outline)
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.surface))
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
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    addView(TextView(this@MainActivity).apply {
                        text = getString(R.string.section_range, course.startSection, course.endSection)
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.secondary))
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = course.title
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                        textSize = 18f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(0, dp(4), 0, dp(5))
                    })
                    val visibleSegments = course.segments.filter { it.occursInWeek(selectedWeek) }
                    val timeLocations = visibleSegments
                        .map { it.location }
                        .ifEmpty { listOf(course.location) }
                        .flatMap { location -> location?.split('、') ?: emptyList() }
                    val clockRanges = timeLocations
                        .mapNotNull { location ->
                            ClassTimeResolver.formatRange(course.startSection, course.endSection, location)
                        }
                        .distinct()
                    val section = getString(R.string.section_range, course.startSection, course.endSection)
                    val time = listOfNotNull(
                        dayName(course.dayOfWeek),
                        course.period,
                        section,
                        clockRanges.singleOrNull(),
                    ).joinToString(" · ")
                    addView(metadataText(getString(R.string.class_time, time)))
                    if (visibleSegments.isNotEmpty()) {
                        visibleSegments.forEach { segment ->
                            val segmentTime = ClassTimeResolver.formatRange(
                                course.startSection,
                                course.endSection,
                                segment.location,
                            )
                            addView(
                                metadataText(
                                    if (clockRanges.size > 1 && segmentTime != null) {
                                        getString(
                                            R.string.class_segment_with_time,
                                            segment.weekText,
                                            segmentTime,
                                            segment.location ?: getString(R.string.not_specified),
                                            segment.teacher ?: getString(R.string.not_specified),
                                        )
                                    } else {
                                        getString(
                                            R.string.class_segment,
                                            segment.weekText,
                                            segment.location ?: getString(R.string.not_specified),
                                            segment.teacher ?: getString(R.string.not_specified),
                                        )
                                    },
                                ),
                            )
                        }
                    } else {
                        addView(metadataText(getString(R.string.class_weeks, course.weekText ?: getString(R.string.not_specified))))
                        addView(metadataText(getString(R.string.class_location, course.location ?: getString(R.string.not_specified))))
                        addView(metadataText(getString(R.string.class_teacher, course.teacher ?: getString(R.string.not_specified))))
                    }
                    course.code?.let { addView(metadataText(getString(R.string.class_code, it))) }
                    course.details.forEach { addView(metadataText(getString(R.string.class_detail, it))) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
    }

    private fun metadataText(value: String) = TextView(this).apply {
        text = value
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
        textSize = 13f
        setLineSpacing(0f, 1.15f)
    }

    private fun calendarDayOfWeek(): Int = ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

    private fun dayName(day: Int): String = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[day - 1]

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
