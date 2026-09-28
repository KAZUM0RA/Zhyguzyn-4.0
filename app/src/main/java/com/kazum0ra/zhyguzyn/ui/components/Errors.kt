package com.kazum0ra.zhyguzyn.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.domain.InputError
import com.kazum0ra.zhyguzyn.update.UpdateError

@Composable
fun InputError.message(limitKm: Double? = null): String {
    val limit = limitKm?.let { Format.km(it) }.orEmpty()
    return when (this) {
        InputError.EMPTY -> stringResource(R.string.error_empty)
        InputError.NOT_A_NUMBER -> stringResource(R.string.error_not_a_number)
        InputError.MUST_BE_POSITIVE -> stringResource(R.string.error_must_be_positive)
        InputError.NEGATIVE -> stringResource(R.string.error_negative)
        InputError.ODOMETER_LESS_THAN_PREVIOUS -> stringResource(R.string.error_odometer_less_than_previous, limit)
        InputError.ODOMETER_GREATER_THAN_NEXT -> stringResource(R.string.error_odometer_greater_than_next, limit)
        InputError.ODOMETER_BELOW_LAST_REFUEL -> stringResource(R.string.error_odometer_below_last, limit)
        InputError.EXCEEDS_TANK -> stringResource(R.string.error_exceeds_tank)
        InputError.TOO_LARGE -> stringResource(R.string.error_too_large)
    }
}

@Composable
fun UpdateError.message(): String = stringResource(
    when (this) {
        UpdateError.NETWORK -> R.string.update_error_network
        UpdateError.NO_RELEASES -> R.string.update_error_no_releases
        UpdateError.RATE_LIMIT -> R.string.update_error_rate_limit
        UpdateError.BAD_RESPONSE -> R.string.update_error_bad_response
        UpdateError.NO_APK -> R.string.update_error_no_apk
        UpdateError.STORAGE -> R.string.update_error_storage
        UpdateError.DOWNLOAD_FAILED -> R.string.update_error_download
    },
)
