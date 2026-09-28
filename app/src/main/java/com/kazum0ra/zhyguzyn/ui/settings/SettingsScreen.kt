package com.kazum0ra.zhyguzyn.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kazum0ra.zhyguzyn.AppContainer
import com.kazum0ra.zhyguzyn.GITHUB_REPOSITORY
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.domain.InputError
import com.kazum0ra.zhyguzyn.domain.SettingsValidator
import com.kazum0ra.zhyguzyn.ui.components.BackTopBar
import com.kazum0ra.zhyguzyn.ui.components.Format
import com.kazum0ra.zhyguzyn.ui.components.NumberField
import com.kazum0ra.zhyguzyn.ui.components.appViewModel
import com.kazum0ra.zhyguzyn.ui.components.message
import com.kazum0ra.zhyguzyn.update.UpdateState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val updateManager = container.updateManager

    var loading by mutableStateOf(true)
        private set
    var capacity by mutableStateOf("")
        private set
    var initial by mutableStateOf("")
        private set
    var capacityError by mutableStateOf<InputError?>(null)
        private set
    var initialError by mutableStateOf<InputError?>(null)
        private set
    var rollover by mutableStateOf("")
        private set
    var rolloverError by mutableStateOf<InputError?>(null)
        private set
    /** Лічильник успішних збережень — для показу повідомлення. */
    var savedCount by mutableStateOf(0)
        private set

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.tankSettings.first()
            capacity = Format.editable(settings.capacityLiters)
            initial = Format.editable(settings.initialFuelLiters)
            rollover = Format.editable(settings.odometerRolloverKm)
            loading = false
        }
    }

    fun onCapacityChange(value: String) {
        capacity = value
        capacityError = null
    }

    fun onInitialChange(value: String) {
        initial = value
        initialError = null
    }

    fun onRolloverChange(value: String) {
        rollover = value
        rolloverError = null
    }

    fun save() {
        when (val result = SettingsValidator.validate(capacity, initial, rollover)) {
            is SettingsValidator.Result.Invalid -> {
                capacityError = result.capacityError
                initialError = result.initialError
                rolloverError = result.rolloverError
            }
            is SettingsValidator.Result.Valid -> viewModelScope.launch {
                container.settingsRepository.saveTankSettings(result.settings)
                savedCount++
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it) }
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.settings_saved)

    LaunchedEffect(viewModel.savedCount) {
        if (viewModel.savedCount > 0) snackbar.showSnackbar(savedText)
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (viewModel.loading) {
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_tank))
            NumberField(
                value = viewModel.capacity,
                onValueChange = viewModel::onCapacityChange,
                label = stringResource(R.string.settings_capacity),
                error = viewModel.capacityError?.message(),
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                value = viewModel.initial,
                onValueChange = viewModel::onInitialChange,
                label = stringResource(R.string.settings_initial),
                error = viewModel.initialError?.message(),
                supportingText = stringResource(R.string.settings_initial_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                value = viewModel.rollover,
                onValueChange = viewModel::onRolloverChange,
                label = stringResource(R.string.settings_rollover),
                error = viewModel.rolloverError?.message(),
                supportingText = stringResource(R.string.settings_rollover_hint),
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save))
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            SectionTitle(stringResource(R.string.settings_updates))
            UpdateSection(viewModel)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun UpdateSection(viewModel: SettingsViewModel) {
    val manager = viewModel.updateManager
    val state by manager.state.collectAsStateWithLifecycle()

    Text(stringResource(R.string.settings_version, manager.currentVersion), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.settings_repo, GITHUB_REPOSITORY),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedButton(
        onClick = manager::checkNow,
        enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state is UpdateState.Checking) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.settings_checking))
        } else {
            Text(stringResource(R.string.settings_check_updates))
        }
    }

    when (val current = state) {
        UpdateState.UpToDate -> Text(stringResource(R.string.settings_up_to_date))
        is UpdateState.Failed -> Text(current.error.message(), color = MaterialTheme.colorScheme.error)
        is UpdateState.Available -> UpdateAvailableRow(current.release.tagName, manager::showDialog)
        is UpdateState.Downloading -> UpdateAvailableRow(current.release.tagName, manager::showDialog)
        is UpdateState.ReadyToInstall -> UpdateAvailableRow(current.release.tagName, manager::showDialog)
        else -> Unit
    }
}

@Composable
private fun UpdateAvailableRow(tag: String, onShow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_update_available, tag), modifier = Modifier.weight(1f))
        TextButton(onClick = onShow) { Text(stringResource(R.string.settings_show_update)) }
    }
}
