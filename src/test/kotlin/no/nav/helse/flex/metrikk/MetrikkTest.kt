package no.nav.helse.flex.metrikk

import no.nav.helse.flex.FellesTestOppsett
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@DirtiesContext
@TestMethodOrder(MethodOrderer.MethodName::class)
@AutoConfigureObservability
class MetrikkTest : FellesTestOppsett() {
    @Autowired
    private lateinit var metrikk: Metrikk

    fun MockMvc.metrikker(): List<String> =
        this
            .perform(get("/internal/prometheus"))
            .andExpect(status().isOk).andReturn().response.contentAsString.split("\n")

    @Test
    fun `1 - initielle metrikker`() {
        val mottattVedtak =
            mockMvc.metrikker().filter { it.contains("mottatt_vedtak_counter") }
        assertThat(mottattVedtak).hasSize(3)

        val counter = mottattVedtak.first { !it.startsWith("#") }
        assertThat(counter).isEqualTo("mottatt_vedtak_counter_total 0.0")
    }

    @Test
    fun `2 - teller mottatt vedtak`() {
        metrikk.mottattVedtakCounter.increment()
        val mottattVedtak = mockMvc.metrikker().filter { it.contains("mottatt_vedtak_counter") }
        assertThat(mottattVedtak).hasSize(3)

        val counter = mottattVedtak.first { !it.startsWith("#") }
        assertThat(counter).isEqualTo("mottatt_vedtak_counter_total 1.0")
    }

    @Test
    fun `3 - teller enda et mottatt vedtak`() {
        metrikk.mottattVedtakCounter.increment()
        val mottattVedtak = mockMvc.metrikker().filter { it.contains("mottatt_vedtak_counter") }
        assertThat(mottattVedtak).hasSize(3)

        val counter = mottattVedtak.first { !it.startsWith("#") }
        assertThat(counter).isEqualTo("mottatt_vedtak_counter_total 2.0")
    }

    @Test
    fun `4 - teller og legger til tag`() {
        metrikk.skalIkkeVises("ikkeSyk").increment()

        val skalIkkeVises = mockMvc.metrikker().filter { it.contains("skal_ikke_vises_counter") }
        assertThat(skalIkkeVises).hasSize(3)

        val counter = skalIkkeVises.first { !it.startsWith("#") }
        assertThat(counter).isEqualTo("""skal_ikke_vises_counter_total{grunn="ikkeSyk",} 1.0""")
    }
}
