package app.ironlog.personal.platform

actual fun deviceCountry(): String = java.util.Locale.getDefault().country
