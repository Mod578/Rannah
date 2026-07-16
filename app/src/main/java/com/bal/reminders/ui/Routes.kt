package com.bal.reminders.ui

import android.net.Uri
import com.bal.reminders.domain.model.Schedule
import java.time.format.DateTimeFormatter

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val LOG = "log"
    const val PERMISSIONS = "permissions"
    const val ABOUT = "about"
    const val DETAILS = "details/{id}"
    const val EDITOR =
        "editor?id={id}&title={title}&type={type}&time={time}&date={date}&days={days}&dom={dom}" +
            "&month={month}&hy={hy}&hm={hm}&hd={hd}&cal={cal}&template={template}"

    fun details(id: Long) = "details/$id"

    fun editorNew() = "editor"

    fun editorEdit(id: Long) = "editor?id=$id"

    fun editorTemplate(templateId: String) = "editor?template=$templateId"

    /** Pre-fills the editor from a parsed draft (any part may be missing). */
    fun editorDraft(title: String?, schedule: Schedule?): String {
        val params = buildList {
            title?.let { add("title=${Uri.encode(it)}") }
            schedule?.let { s ->
                add("time=${s.time.format(DateTimeFormatter.ofPattern("HH:mm"))}")
                when (s) {
                    is Schedule.Once -> {
                        add("type=once")
                        add("date=${s.date}")
                    }
                    is Schedule.OnceHijri -> {
                        add("type=once")
                        add("cal=hijri")
                        add("hy=${s.year}")
                        add("hm=${s.month}")
                        add("hd=${s.day}")
                    }
                    is Schedule.Daily -> add("type=daily")
                    is Schedule.Weekly -> {
                        add("type=weekly")
                        add("days=${s.days.joinToString(",") { it.value.toString() }}")
                    }
                    is Schedule.Monthly -> {
                        add("type=monthly")
                        add("dom=${s.dayOfMonth}")
                    }
                    is Schedule.HijriMonthly -> {
                        add("type=hijri_monthly")
                        add("dom=${s.dayOfMonth}")
                    }
                    is Schedule.Yearly -> {
                        add("type=yearly")
                        add("month=${s.month}")
                        add("dom=${s.day}")
                    }
                    is Schedule.HijriYearly -> {
                        add("type=hijri_yearly")
                        add("month=${s.month}")
                        add("dom=${s.day}")
                    }
                }
            }
        }
        return if (params.isEmpty()) "editor" else "editor?${params.joinToString("&")}"
    }
}
