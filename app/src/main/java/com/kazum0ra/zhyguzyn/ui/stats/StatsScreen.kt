package com.kazum0ra.zhyguzyn.ui.stats

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
import androidx.compose.material3.HorizontalDivider
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
import com.kazum0ra.zhyguzyn.ui.components.AppCard
import com.kazum0ra.zhyguzyn.ui.components.BackTopBar
import com.kazum0ra.zhyguzyn.ui.components.Format
import com.kazum0ra.zhyguzyn.ui.components.ValueColumn
import com.kazum0ra.zhyguzyn.ui.components.appViewModel
import com.kazum0ra.zhyguzyn.ui.theme.AppColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(container: AppContainer) : ViewModel() {
    val overview: StateFlow<FuelOverview?> = container.overview
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun StatsScreen() {
    val viewModel = appViewModel { StatsViewModel(it) }
    val overview by viewModel.overview.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.stats_title), onBack = null) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SummaryCard(current)
            IntervalsCard(current)
        }
    }
}

@Composable
private fun SummaryCard(overview: FuelOverview) {
    val stats = overview.stats
    val unitL100 = stringResource(R.string.unit_l100)
    AppCard {
        Text(
            stringResource(R.string.stats_summary),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            ValueColumn(
                label = stringResource(R.string.stats_refuels),
                value = stats.refuelCount.toString(),
                modifier = Modifier.weight(1f),
            )
            ValueColumn(
                label = stringResource(R.string.stats_distance),
                value = "${Format.km(Math.round(stats.totalDistanceKm).toDouble())} ${stringResource(R.string.unit_km)}",
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            ValueColumn(
                label = stringResource(R.string.stats_liters),
                value = "${Format.liters(stats.totalLiters)} ${stringResource(R.string.unit_liters)}",
                modifier = Modifier.weight(1f),
            )
            ValueColumn(
                label = stringResource(R.string.stats_avg),
                value = stats.averageConsumption?.let { "${Format.consumption(it)} $unitL100" } ?: "—",
                hint = when (stats.consumptionSource) {
                    ConsumptionSource.FULL_TO_FULL -> stringResource(R.string.home_source_full)
                    ConsumptionSource.ALL_REFUELS -> stringResource(R.string.home_source_all)
                    ConsumptionSource.MANUAL -> stringResource(R.string.home_source_manual)
                    null -> null
                },
                modifier = Modifier.weight(1f),
            )
        }
        val best = stats.intervals.minByOrNull { it.litersPer100Km }
        val worst = stats.intervals.maxByOrNull { it.litersPer100Km }
        if (best != null && worst != null && stats.intervals.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                ValueColumn(
                    label = stringResource(R.string.stats_best),
                    value = "${Format.consumption(best.litersPer100Km)} $unitL100",
                    modifier = Modifier.weight(1f),
                )
                ValueColumn(
                    label = stringResource(R.string.stats_worst),
                    value = "${Format.consumption(worst.litersPer100Km)} $unitL100",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun IntervalsCard(overview: FuelOverview) {
    val intervals = overview.stats.intervals
    val dates = overview.refuels.associate { it.id to it.date }
    AppCard {
        Text(
            stringResource(R.string.stats_intervals),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
        if (intervals.isEmpty()) {
            Text(
                stringResource(R.string.stats_intervals_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@AppCard
        }
        // Найновіші першими.
        intervals.asReversed().forEachIndexed { index, interval ->
            if (index > 0) HorizontalDivider(color = AppColors.CardBorder)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val date = dates[interval.endRefuelId]
                    Text(
                        date?.let { stringResource(R.string.stats_interval_title, Format.shortDate(it)) }.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(
                            R.string.stats_interval_details,
                            Format.km(Math.round(interval.distanceKm).toDouble()),
                            Format.liters(interval.liters),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                    )
                }
                Text(
                    "${Format.consumption(interval.litersPer100Km)} ${stringResource(R.string.unit_l100)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
