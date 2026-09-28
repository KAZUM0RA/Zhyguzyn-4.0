package com.kazum0ra.zhyguzyn.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.domain.ConsumptionSource
import com.kazum0ra.zhyguzyn.domain.FuelStats
import com.kazum0ra.zhyguzyn.ui.components.Format
import com.kazum0ra.zhyguzyn.ui.components.FuelGauge
import com.kazum0ra.zhyguzyn.ui.components.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(container: AppContainer) : ViewModel() {
    /** null — дані ще завантажуються. */
    val stats: StateFlow<FuelStats?> = container.overview
        .map { it.stats }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddRefuel: () -> Unit,
    onEstimate: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(it) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.home_history))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                },
            )
        },
    ) { padding ->
        val current = stats
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
            FuelCard(current)
            StatsCard(current)

            Button(onClick = onAddRefuel, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.home_add))
            }
            OutlinedButton(
                onClick = onEstimate,
                enabled = current.hasEntries,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.home_estimate))
            }
        }
    }
}

@Composable
private fun FuelCard(stats: FuelStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_fuel_left), style = MaterialTheme.typography.titleMedium)
            val fuel = stats.fuelAfterLastRefuel
            if (!stats.hasEntries) {
                Text(stringResource(R.string.home_no_entries), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            if (fuel != null) {
                Text(
                    stringResource(R.string.home_fuel_value, Format.liters(fuel), Format.liters(stats.capacityLiters)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(stringResource(R.string.home_fuel_unknown), style = MaterialTheme.typography.titleLarge)
            }
            val fraction = if (fuel != null && stats.capacityLiters > 0) (fuel / stats.capacityLiters).toFloat() else null
            FuelGauge(fraction)
            stats.lastOdometerKm?.let {
                Text(
                    stringResource(R.string.home_fuel_left_at, Format.km(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(stats: FuelStats) {
    if (!stats.hasEntries) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val average = stats.averageConsumption
            if (average == null) {
                Text(stringResource(R.string.home_not_enough_data), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            StatRow(
                label = stringResource(R.string.home_avg),
                value = "${Format.consumption(average)} ${stringResource(R.string.unit_l100)}",
                hint = when (stats.consumptionSource) {
                    ConsumptionSource.FULL_TO_FULL -> stringResource(R.string.home_source_full)
                    ConsumptionSource.ALL_REFUELS -> stringResource(R.string.home_source_all)
                    ConsumptionSource.MANUAL -> stringResource(R.string.home_source_manual)
                    null -> null
                },
            )
            stats.lastIntervalConsumption?.let {
                StatRow(
                    label = stringResource(R.string.home_last_interval),
                    value = "${Format.consumption(it)} ${stringResource(R.string.unit_l100)}",
                )
            }
            stats.rangeKm?.let {
                StatRow(
                    label = stringResource(R.string.home_range),
                    value = "≈ ${Format.km(Math.round(it).toDouble())} ${stringResource(R.string.unit_km)}",
                    hint = stringResource(R.string.home_range_hint),
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, hint: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}
