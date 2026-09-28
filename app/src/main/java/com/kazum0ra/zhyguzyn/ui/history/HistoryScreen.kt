package com.kazum0ra.zhyguzyn.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.kazum0ra.zhyguzyn.ui.theme.AppColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kazum0ra.zhyguzyn.AppContainer
import com.kazum0ra.zhyguzyn.FuelOverview
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.domain.Refuel
import com.kazum0ra.zhyguzyn.ui.components.BackTopBar
import com.kazum0ra.zhyguzyn.ui.components.Format
import com.kazum0ra.zhyguzyn.ui.components.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val overview: StateFlow<FuelOverview?> = container.overview
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(refuel: Refuel) {
        viewModelScope.launch { container.fuelRepository.delete(refuel) }
    }
}

@Composable
fun HistoryScreen(onEdit: (Long) -> Unit) {
    val viewModel = appViewModel { HistoryViewModel(it) }
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Refuel?>(null) }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.history_title), onBack = null) },
        containerColor = AppColors.Background,
    ) { padding ->
        val current = overview
        when {
            current == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            current.refuels.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(current.refuels, key = { it.id }) { refuel ->
                    RefuelItem(
                        refuel = refuel,
                        consumption = current.stats.consumptionEndingAt(refuel.id),
                        onEdit = { onEdit(refuel.id) },
                        onDelete = { toDelete = refuel },
                    )
                }
            }
        }
    }

    toDelete?.let { refuel ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.entry_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.history_delete_confirm_text,
                        Format.date(refuel.date),
                        Format.km(refuel.odometerKm),
                        Format.liters(refuel.liters),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(refuel)
                    toDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RefuelItem(refuel: Refuel, consumption: Double?, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card)
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Format.date(refuel.date), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            Text(
                stringResource(R.string.history_item_title, Format.km(refuel.odometerKm), Format.liters(refuel.liters)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            val tank = stringResource(if (refuel.fullTank) R.string.history_full else R.string.history_partial)
            val text = consumption
                ?.let { "$tank · ${stringResource(R.string.history_consumption, Format.consumption(it))}" }
                ?: tank
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (refuel.fullTank) AppColors.Green else AppColors.TextSecondary,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit), tint = AppColors.TextSecondary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete), tint = AppColors.TextSecondary)
        }
    }
}
