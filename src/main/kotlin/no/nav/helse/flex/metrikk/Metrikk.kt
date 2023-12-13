package no.nav.helse.flex.metrikk

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.stereotype.Component

@Component
class Metrikk(registry: MeterRegistry) {
    val reg = registry

    val mottattVedtakCounter = registry.counter("mottatt_vedtak_counter")
    val mottattManueltVedtakCounter = registry.counter("mottatt_manuelt_vedtak_counter")
    val mottattAutomatiskVedtakCounter = registry.counter("mottatt_automatisk_vedtak_counter")
    val mottattAnnulleringVedtakCounter = registry.counter("mottatt_annullering_vedtak_counter")
    val statusMotattCounter = registry.counter("status_motatt_counter")

    fun vedtaktype(type: String) =
        reg.counter(
            "vedtak_type_counter",
            Tags.of("type", type),
        )

    fun skalIkkeVises(grunn: String) =
        reg.counter(
            "skal_ikke_vises_counter",
            Tags.of("grunn", grunn),
        )
}
