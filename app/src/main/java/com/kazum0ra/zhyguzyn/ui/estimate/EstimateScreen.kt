package com.kazum0ra.zhyguzyn.ui.estimate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kazum0ra.zhyguzyn.AppContainer
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.domain.Estimate
import com.kazum0ra.zhyguzyn.domain.FuelCalculator
import com.kazum0ra.zhyguzyn.domain.FuelStats
import com.kazum0ra.zhyguzyn.domain.NumberParser
import com.kazum0ra.zhyguzyn.ui.components.BackTopBar
import com.kazum0ra.zhyguzyn.ui.components.EmptyAtColumn
import com.kazum0ra.zhyguzyn.ui.components.Format
import com.kazum0ra.zhyguzyn.ui.components.FuelGauge
import com.kazum0ra.zhyguzyn.ui.components.NumberField
import com.kazum0ra.zhyguzyn.ui.components.appViewModel
import com.kazum0ra.zhyguzyn.ui.components.message
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class EstimateMode { ODOMETER, DISTANCE }

/**
 * Стан живе лише поки екран у стеку навігації: при виході ViewModel знищується,
 * тож поле очищається, а в базу нічого не пишеться.
 */
class EstimateViewModel(container: AppContainer) : ViewModel() {
    val stats: StateFlow<FuelStats?> = container.overview
        .map { it.stats }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var mode by mutableStateOf(EstimateMode.ODOMETER)
        private set
    var input by mutableStateOf("")
        private set

    fun onModeChange(value: EstimateMode) {
        if (value != mode) {
            mode = value
            input = ""
        }
    }

    fun onInputChange(value: String) {
        input = value
    }
}

/** null — поле порожнє, розрахунку ще немає. */
fun estimate(stats: FuelStats, mode: EstimateMode, input: String): Estimate? {
    if (!stats.hasEntries) return Estimate.NoEntries
    if (input.isBlank()) return null
    val value = when (val parsed = NumberParser.parse(input)) {
        is NumberParser.Result.Error -> return Estimate.Invalid(parsed.error)
        is NumberParser.Result.Ok -> parsed.value
    }
    return when (mode) {
        EstimateMode.ODOMETER -> FuelCalculator.estimateByOdometer(stats, value)
        EstimateMode.DISTANCE -> FuelCalculator.estimateByDistance(stats, value)
    }
}

@Composable
fun EstimateScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { EstimateViewModel(it) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackTopBar(stringResource(R.string.estimate_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                EstimateMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewModel.mode == mode,
                        onClick = { viewModel.onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, EstimateMode.entries.size),
                    ) {
                        Text(
                            stringResource(
                                if (mode == EstimateMode.ODOMETER) R.string.estimate_mode_odometer
                                else R.string.estimate_mode_distance,
                            ),
                        )
                    }
                }
            }

            val current = stats ?: return@Column
            val result = estimate(current, viewModel.mode, viewModel.input)
            val invalid = result as? Estimate.Invalid

            NumberField(
                value = viewModel.input,
                onValueChange = viewModel::onInputChange,
                label = stringResource(
                    if (viewModel.mode == EstimateMode.ODOMETER) R.string.estimate_input_odometer
                    else R.string.estimate_input_distance,
                ),
                error = invalid?.let { it.error.message(it.limitKm) },
                supportingText = current.lastOdometerKm?.let {
                    stringResource(R.string.estimate_last_refuel, Format.km(it))
                },
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )

            when (result) {
                null -> Hint(stringResource(R.string.estimate_enter_value))
                Estimate.NoEntries -> Hint(stringResource(R.string.home_no_entries))
                Estimate.NotEnoughData -> Hint(stringResource(R.string.home_not_enough_data))
                is Estimate.Invalid -> Unit
                is Estimate.Ok -> EstimateResult(result)
            }

            Text(
                stringResource(R.string.estimate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun EstimateResult(result: Estimate.Ok) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.estimate_fuel_left), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_fuel_value, Format.liters(result.fuelLeftLiters), Format.liters(result.capacityLiters)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val fraction = if (result.capacityLiters > 0) (result.fuelLeftLiters / result.capacityLiters).toFloat() else 0f
            FuelGauge(fraction)
            Text(stringResource(R.string.estimate_range), style = MaterialTheme.typography.titleMedium)
            Text(
                "≈ ${Format.km(Math.round(result.rangeKm).toDouble())} ${stringResource(R.string.unit_km)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (result.fuelLeftLiters > 0.0) {
                result.emptyAtOdometer?.let { EmptyAtColumn(it, Modifier.padding(top = 8.dp)) }
            } else {
                Text(
                    stringResource(R.string.estimate_empty_tank),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
