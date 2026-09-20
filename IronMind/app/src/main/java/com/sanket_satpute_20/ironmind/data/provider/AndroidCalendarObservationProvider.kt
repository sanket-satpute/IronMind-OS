package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.sanket_satpute_20.ironmind.domain.model.observation.CalendarEvent
import com.sanket_satpute_20.ironmind.domain.provider.CalendarObservationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidCalendarObservationProvider(
    private val context: Context
) : CalendarObservationProvider {

    override fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun getUpcomingEvents(fromTime: Long, toTime: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!isPermissionGranted()) {
            return@withContext emptyList()
        }

        val events = mutableListOf<CalendarEvent>()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END
        )

        // Using CalendarContract.Instances to get occurrences within the time range
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, fromTime)
        android.content.ContentUris.appendId(builder, toTime)

        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)

                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleIndex) ?: "Untitled Event"
                    val begin = cursor.getLong(beginIndex)
                    val end = cursor.getLong(endIndex)
                    events.add(CalendarEvent(title, begin, end))
                }
            }
        } catch (e: Exception) {
            println("AndroidCalendarObservationProvider: Failed to fetch events - ${e.message}")
        }

        return@withContext events
    }
}
