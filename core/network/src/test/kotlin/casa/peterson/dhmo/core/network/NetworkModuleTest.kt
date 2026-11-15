package casa.peterson.dhmo.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun `client has the planned timeouts and no interceptors`() {
        val client = NetworkModule.okHttpClient()
        assertThat(client.connectTimeoutMillis).isEqualTo(15_000)
        assertThat(client.readTimeoutMillis).isEqualTo(30_000)
        assertThat(client.interceptors).isEmpty()
    }

    @Test
    fun `json ignores unknown keys`() {
        val json = NetworkModule.json()
        assertThat(json.decodeFromString<Map<String, Int>>("""{"a":1}""")).containsExactly("a", 1)
        assertThat(json.configuration.ignoreUnknownKeys).isTrue()
    }
}
