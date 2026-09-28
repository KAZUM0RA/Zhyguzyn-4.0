package com.kazum0ra.zhyguzyn.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazum0ra.zhyguzyn.AppContainer
import com.kazum0ra.zhyguzyn.FuelOverview
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.domain.InputError
import com.kazum0ra.zhyguzyn.domain.Refuel
import com.kazum0ra.zhyguzyn.domain.RefuelValidator
import com.kazum0ra.zhyguzyn.ui.components.BackTopBar
import com.kazum0ra.zhyguzyn.ui.components.Format
import com.kazum0ra.zhyguzyn.ui.components.NumberField
import com.kazum0ra.zhyguzyn.ui.components.appViewModel
import com.kazum0ra.zhyguzyn.ui.components.message
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

class EntryViewModel(
    private val container: AppContainer,
    /** null — нова заправка. */
    private val editingId: Long?,
) : ViewModel() {

    var loading by mutableStateOf(editingId != null)
        private set
    var notFound by mutableStateOf(false)
        private set
    var odometer by mutableStateOf("")
        private set
    var liters by mutableStateOf("")
        private set
    var fullTank by mutableStateOf(false)
        private set
    var date: LocalDate by mutableStateOf(LocalDate.now())
        private set
    var odometerError by mutableStateOf<Pair<InputError, Double?>?>(null)
        private set
    var litersError by mutableStateOf<InputError?>(null)
        private set
    /** Пробіг попередньої заправки — підказка під полем. */
    var previousOdometer by mutableStateOf<Double?>(null)
        private set
    var finished by mutableStateOf(false)
        private set

    val isEditing: Boolean get() = editingId != null

    private var original: Refuel? = null

    init {
        viewModelScope.launch {
            val overview = container.overview.first()
            if (editingId != null) {
                val refuel = container.fuelRepository.get(editingId)
                if (refuel == null) {
                    notFound = true
                } else {
                    original = refuel
                    odometer = Format.editable(refuel.odometerKm)
                    liters = Format.editable(refuel.liters)
                    fullTank = refuel.fullTank
                    date = refuel.date
                }
                loading = false
            }
            updatePreviousOdometer(overview)
        }
    }

    private fun updatePreviousOdometer(overview: FuelOverview) {
        previousOdometer = RefuelValidator.previousOdometer(
            overview.refuels,
            editingId,
            date,
            overview.settings.odometerRolloverKm,
        )
    }

    fun onOdometerChange(value: String) {
        odometer = value
        odometerError = null
    }

    fun onLitersChange(value: String) {
        liters = value
        litersError = null
    }

    fun onFullTankChange(value: Boolean) {
        fullTank = value
    }

    fun onDateChange(value: LocalDate) {
        date = value
        odometerError = null
        viewModelScope.launch { updatePreviousOdometer(container.overview.first()) }
    }

    fun save() {
        viewModelScope.launch {
            val overview = container.overview.first()
            val result = RefuelValidator.validate(
                odometerText = odometer,
                litersText = liters,
                date = date,
                existing = overview.refuels,
                editingId = editingId,
                tankCapacityLiters = overview.settings.capacityLiters,
                odometerRolloverKm = overview.settings.odometerRolloverKm,
            )
            when (result) {
                is RefuelValidator.Result.Invalid -> {
                    odometerError = result.odometerError?.let { it to result.odometerLimitKm }
                    litersError = result.litersError
                }
                is RefuelValidator.Result.Valid -> {
                    container.fuelRepository.save(
                        Refuel(
                            id = editingId ?: 0L,
                            date = date,
                            odometerKm = result.odometerKm,
                            liters = result.liters,
                            fullTank = fullTank,
                        ),
                    )
                    finished = true
                }
            }
        }
    }

    fun delete() {
        val refuel = original ?: return
        viewModelScope.launch {
            container.fuelRepository.delete(refuel)
            finished = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(entryId: Long?, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "entry-$entryId") { EntryViewModel(it, entryId) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.finished) {
        if (viewModel.finished) onDone()
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (viewModel.isEditing) R.string.entry_edit_title else R.string.entry_add_title),
                onBack = onDone,
                actions = {
                    if (viewModel.isEditing && !viewModel.notFound) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            viewModel.notFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.entry_not_found))
            }
            else -> EntryForm(viewModel, Modifier.padding(padding), onPickDate = { showDatePicker = true })
        }
    }

    if (showDatePicker) {
        EntryDatePicker(
            initial = viewModel.date,
            onDismiss = { showDatePicker = false },
            onPicked = {
                viewModel.onDateChange(it)
                showDatePicker = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.entry_delete_confirm_title)) },
            text = { Text(stringResource(R.string.entry_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun EntryForm(viewModel: EntryViewModel, modifier: Modifier, onPickDate: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NumberField(
            value = viewModel.odometer,
            onValueChange = viewModel::onOdometerChange,
            label = stringResource(R.string.entry_odometer),
            error = viewModel.odometerError?.let { (error, limit) -> error.message(limit) },
            supportingText = viewModel.previousOdometer?.let {
                stringResource(R.string.entry_last_odometer, Format.km(it))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(
            value = viewModel.liters,
            onValueChange = viewModel::onLitersChange,
            label = stringResource(R.string.entry_liters),
            error = viewModel.litersError?.message(),
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.onFullTankChange(!viewModel.fullTank) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.entry_full_tank), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.entry_full_tank_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = viewModel.fullTank, onCheckedChange = viewModel::onFullTankChange)
        }

        Box {
            OutlinedTextField(
                value = Format.date(viewModel.date),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.entry_date)) },
                trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            // Прозорий шар над полем, щоб увесь рядок відкривав календар.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(onClick = onPickDate),
            )
        }

        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(56.dp),
        ) {
            Text(stringResource(R.string.save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDatePicker(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val todayMillis = LocalDate.now().toEpochDay() * MILLIS_PER_DAY
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(Math.floorDiv(it, MILLIS_PER_DAY))) }
                    ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
