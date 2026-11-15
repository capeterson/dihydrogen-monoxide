package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.data.mapper.toEntity
import casa.peterson.dhmo.core.data.mapper.toModel
import casa.peterson.dhmo.core.database.dao.WeatherDao
import casa.peterson.dhmo.core.model.WeatherDay
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [WeatherRepository]. */
class OfflineWeatherRepository @Inject constructor(private val dao: WeatherDao) : WeatherRepository {
    override fun observeDay(day: LocalDate): Flow<WeatherDay?> = dao.observeDay(day).map { it?.toModel() }

    override suspend fun forDay(day: LocalDate): WeatherDay? = dao.getDay(day)?.toModel()

    override suspend fun range(from: LocalDate, to: LocalDate): List<WeatherDay> = dao.getRange(from, to).map { it.toModel() }

    override suspend fun save(day: WeatherDay) {
        dao.upsert(day.toEntity())
    }
}
