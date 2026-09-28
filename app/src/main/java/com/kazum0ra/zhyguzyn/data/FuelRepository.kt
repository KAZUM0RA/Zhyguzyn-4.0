package com.kazum0ra.zhyguzyn.data

import com.kazum0ra.zhyguzyn.domain.Refuel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FuelRepository(private val dao: FuelDao) {

    /** Усі заправки, найновіші першими. */
    val refuels: Flow<List<Refuel>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Refuel? = dao.getById(id)?.toDomain()

    /** Додає нову заправку (id == 0) або оновлює наявну. */
    suspend fun save(refuel: Refuel) {
        if (refuel.id == 0L) dao.insert(refuel.toEntity()) else dao.update(refuel.toEntity())
    }

    suspend fun delete(refuel: Refuel) = dao.delete(refuel.toEntity())
}
