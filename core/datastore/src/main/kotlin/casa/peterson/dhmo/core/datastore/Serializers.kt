package casa.peterson.dhmo.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.DeviceState
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal val DataStoreJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

internal abstract class JsonSerializer<T : Any>(private val serializer: KSerializer<T>, override val defaultValue: T) : Serializer<T> {
    protected open fun postProcess(decoded: T): T = decoded

    override suspend fun readFrom(input: InputStream): T = try {
        postProcess(DataStoreJson.decodeFromString(serializer, input.readBytes().decodeToString()))
    } catch (error: SerializationException) {
        throw CorruptionException("Cannot read ${serializer.descriptor.serialName}", error)
    } catch (error: IllegalArgumentException) {
        throw CorruptionException("Cannot read ${serializer.descriptor.serialName}", error)
    }

    @Throws(IOException::class)
    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(DataStoreJson.encodeToString(serializer, t).encodeToByteArray())
    }
}

/** Decodes, migrates, then sanitises. */
internal object AppSettingsSerializer : JsonSerializer<AppSettings>(AppSettings.serializer(), AppSettings()) {
    override fun postProcess(decoded: AppSettings): AppSettings = SettingsValidator.sanitize(SettingsMigrations.migrate(decoded))
}

internal object DeviceStateSerializer : JsonSerializer<DeviceState>(DeviceState.serializer(), DeviceState())
