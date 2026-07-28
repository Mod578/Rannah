package com.bal.reminders.ui

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val LICENSES = "licenses"
    const val DETAILS = "details/{id}"
    const val EDITOR = "editor?id={id}"

    fun details(id: Long) = "details/$id"

    fun editorNew() = "editor"

    fun editorEdit(id: Long) = "editor?id=$id"
}
