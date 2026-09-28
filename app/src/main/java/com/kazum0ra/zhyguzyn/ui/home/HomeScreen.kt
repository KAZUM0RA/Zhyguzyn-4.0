package com.kazum0ra.zhyguzyn.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kazum0ra.zhyguzyn.AppContainer
import com.kazum0ra.zhyguzyn.FuelOverview
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.domain.ConsumptionSource
import com.kazum0ra.zhyguzyn.domain.Refuel
import com.kazum0ra.zhyguzyn.ui.components.AppCard
import com.kazum0ra.zhyguzyn.ui.components.BackTopBar
import com.kazum0ra.zhyguzyn.ui.components.BigActionButton
import com.kazum0ra.zhyguzyn.ui.components.Format
import com.kazum0ra.zhyguzyn.ui.components.FuelGauge
import com.kazum0ra.zhyguzyn.ui.components.ValueColumn
import com.kazum0ra.zhyguzyn.ui.components.appViewModel
import com.kazum0ra.zhyguzyn.ui.theme.AppColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(container: AppContainer) : ViewModel() {
    /** null — дані ще завантажуються. */
    val overview: StateFlow<FuelOverview?> = container.overview
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HomeScreen(onAddRefuel: () -> Unit, onEstimate: () -> Unit) {
    val viewModel = appViewModel { HomeViewModel(it) }
    val overview by viewModel.overview.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.home_title), onBack = null) },
        containerColor = AppColors.Background,
    ) { padding ->
        val current = overview
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LastRefuelCard(current.refuels.firstOrNull())
            FuelCard(current)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigActionButton(
                    text = stringResource(R.string.home_add),
                    onClick = onAddRefuel,
                    modifier = Modifier.weight(1f),
                )
                BigActionButton(
                    text = stringResource(R.string.home_estimate),
                    onClick = onEstimate,
                    enabled = current.stats.hasEntries,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LastRefuelCard(last: Refuel?) {
    AppCard {
        if (last == null) {
            Text(
                stringResource(R.string.home_no_entries),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
            )
            return@AppCard
        }
        Text(
            stringResource(R.string.home_last_refuel, Format.date(last.date)),
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextSecondary,
        )
        Text(
            stringResource(
                R.string.home_last_refuel_details,
                Format.km(last.odometerKm),
                Format.liters(last.liters),
                stringResource(if (last.fullTank) R.string.history_full else R.string.history_partial),
            ),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FuelCard(overview: FuelOverview) {
    val stats = overview.stats
    val unitL100 = stringResource(R.string.unit_l100)
    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_fuel),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.home_tank, Format.liters(stats.capacityLiters)),
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextSecondary,
            )
        }

        val fuel = stats.fuelAfterLastRefuel
        val fraction = if (fuel != null && stats.capacityLiters > 0) (fuel / stats.capacityLiters).toFloat() else null
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
        ) {
            Text(
                if (fuel != null) "≈ ${Format.liters(fuel)} ${stringResource(R.string.unit_liters)}" else "—",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (fraction != null) {
                Text(
                    "  ≈ ${(fraction.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        FuelGauge(fraction)

        if (stats.hasEntries) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                ValueColumn(
                    label = stringResource(R.string.home_range),
                    value = stats.rangeKm
                        ?.let { "≈ ${Format.km(Math.round(it).toDouble())} ${stringResource(R.string.unit_km)}" }
                        ?: "—",
                    modifier = Modifier.weight(1f),
                )
                ValueColumn(
                    label = stringResource(R.string.home_avg),
                    value = stats.averageConsumption?.let { "≈ ${Format.consumption(it)} $unitL100" } ?: "—",
                    hint = when (stats.consumptionSource) {
                        ConsumptionSource.FULL_TO_FULL -> stringResource(R.string.home_source_full)
                        ConsumptionSource.ALL_REFUELS -> stringResource(R.string.home_source_all)
                        ConsumptionSource.MANUAL -> stringResource(R.string.home_source_manual)
                        null -> null
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val last = overview.refuels.firstOrNull()
        val note = when {
            last == null -> null
            stats.averageConsumption == null -> stringResource(R.string.home_not_enough_data)
            else -> stringResource(R.string.home_estimate_note, Format.shortDate(last.date), Format.km(last.odometerKm))
        }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
