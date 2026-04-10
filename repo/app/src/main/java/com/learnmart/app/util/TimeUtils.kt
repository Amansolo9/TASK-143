package com.learnmart.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

object TimeUtils {
    fun nowUtc(): Instant = Instant.now()

    fun nowZoned(): ZonedDateTime = ZonedDateTime.now()

    fun todayLocal(): LocalDate = LocalDate.now()

    fun isExpired(expiresAt: Instant): Boolean = Instant.now().isAfter(expiresAt)

    fun minutesFromNow(minutes: Long): Instant =
        Instant.now().plusSeconds(minutes * 60)

    fun hoursFromNow(hours: Long): Instant =
        Instant.now().plusSeconds(hours * 3600)

    fun daysFromNow(days: Long): Instant =
        Instant.now().plusSeconds(days * 86400)
}

object IdGenerator {
    fun newId(): String = UUID.randomUUID().toString()
}
