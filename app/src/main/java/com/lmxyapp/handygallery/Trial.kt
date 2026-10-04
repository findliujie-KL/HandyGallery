package com.lmxyapp.handygallery

import android.content.Context
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Time limit for trial builds. The last valid day is baked in at build time (BuildConfig.TRIAL_EXPIRY,
 * empty for an unlimited build); after it, the two headline features stop while the gallery and editor
 * keep working. It trusts the device clock, so it is a courtesy limit rather than real protection.
 */
object Trial {
    /** The last day the trial works, or null for a build that never expires. */
    val lastDay: LocalDate? = BuildConfig.TRIAL_EXPIRY.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)

    val expired: Boolean get() = lastDay != null && LocalDate.now().isAfter(lastDay)

    fun lastDayText(): String = lastDay?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)).orEmpty()

    /** True, after telling the user why, when a trial-limited action must not go ahead. */
    fun blocks(context: Context): Boolean {
        if (!expired) return false
        Toast.makeText(context, context.getString(R.string.trial_expired, lastDayText()), Toast.LENGTH_LONG).show()
        return true
    }
}
