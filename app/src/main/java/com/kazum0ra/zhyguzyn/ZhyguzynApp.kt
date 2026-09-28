package com.kazum0ra.zhyguzyn

import android.app.Application
import com.kazum0ra.zhyguzyn.data.AppDatabase
import com.kazum0ra.zhyguzyn.data.FuelRepository
import com.kazum0ra.zhyguzyn.data.SettingsRepository
import com.kazum0ra.zhyguzyn.domain.FuelCalculator
import com.kazum0ra.zhyguzyn.domain.FuelStats
import com.kazum0ra.zhyguzyn.domain.Refuel
import com.kazum0ra.zhyguzyn.domain.TankSettings
import com.kazum0ra.zhyguzyn.update.UpdateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Ручне впровадження залежностей: усі синглтони живуть тут. */
class ZhyguzynApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val database = AppDatabase.create(app)
    val fuelRepository = FuelRepository(database.fuelDao())
    val settingsRepository = SettingsRepository(app)
    val updateManager = UpdateManager(app, settingsRepository)

    /** Заправки, налаштування і розрахована статистика — перераховується після кожної зміни. */
    val overview: Flow<FuelOverview> =
        combine(fuelRepository.refuels, settingsRepository.tankSettings) { refuels, settings ->
            FuelOverview(refuels, settings, FuelCalculator.calculate(refuels, settings))
        }
}

data class FuelOverview(
    /** Найновіші першими. */
    val refuels: List<Refuel>,
    val settings: TankSettings,
    val stats: FuelStats,
)
