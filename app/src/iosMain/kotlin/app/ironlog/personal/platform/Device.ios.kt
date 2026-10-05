package app.ironlog.personal.platform

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale

actual fun deviceCountry(): String = NSLocale.currentLocale.countryCode ?: ""
