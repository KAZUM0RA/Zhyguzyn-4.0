package com.kazum0ra.zhyguzyn.ui.components

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Форматування чисел і дат українською. */
object Format {
    val locale: Locale = Locale.forLanguageTag("uk-UA")

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", locale)

    private fun number(value: Double, maxDecimals: Int, minDecimals: Int = 0): String =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = maxDecimals
            minimumFractionDigits = minDecimals
            isGroupingUsed = true
        }.format(value)

    /** Кілометри: 12 345 або 12 345,5 */
    fun km(value: Double): String = number(value, maxDecimals = 1)

    /** Літри: 45,5 */
    fun liters(value: Double): String = number(value, maxDecimals = 1)

    /** Витрата: 7,35 */
    fun consumption(value: Double): String = number(value, maxDecimals = 2, minDecimals = 1)

    fun date(date: LocalDate): String = dateFormatter.format(date)

    fun shortDate(date: LocalDate): String = shortDateFormatter.format(date)

    /** Значення для поля вводу без групування: 12345 або 45,5 */
    fun editable(value: Double): String =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
            .toPlainString().replace('.', ',')
}
