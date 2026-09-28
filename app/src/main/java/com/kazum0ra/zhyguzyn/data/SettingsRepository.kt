package com.kazum0ra.zhyguzyn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kazum0ra.zhyguzyn.domain.TankSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val TANK_CAPACITY = doublePreferencesKey("tank_capacity_liters")
        val INITIAL_FUEL = doublePreferencesKey("initial_fuel_liters")
        val ODOMETER_ROLLOVER = doublePreferencesKey("odometer_rollover_km")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_millis")
    }

    val tankSettings: Flow<TankSettings> = store.data.map { prefs ->
        TankSettings(
            capacityLiters = prefs[Keys.TANK_CAPACITY] ?: TankSettings.DEFAULT.capacityLiters,
            initialFuelLiters = prefs[Keys.INITIAL_FUEL] ?: TankSettings.DEFAULT.initialFuelLiters,
            odometerRolloverKm = prefs[Keys.ODOMETER_ROLLOVER] ?: TankSettings.DEFAULT.odometerRolloverKm,
        )
    }

    suspend fun saveTankSettings(settings: TankSettings) {
        store.edit { prefs ->
            prefs[Keys.TANK_CAPACITY] = settings.capacityLiters
            prefs[Keys.INITIAL_FUEL] = settings.initialFuelLiters
            prefs[Keys.ODOMETER_ROLLOVER] = settings.odometerRolloverKm
        }
    }

    suspend fun lastUpdateCheckMillis(): Long = store.data.map { it[Keys.LAST_UPDATE_CHECK] ?: 0L }.first()

    suspend fun setLastUpdateCheckMillis(millis: Long) {
        store.edit { it[Keys.LAST_UPDATE_CHECK] = millis }
    }
}
