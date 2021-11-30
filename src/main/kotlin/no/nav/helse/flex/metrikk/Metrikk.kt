package no.nav.helse.flex.metrikk

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class Metrikk(registry: MeterRegistry) {

    val MOTTATT_VEDTAK = registry.counter(
        "mottatt_vedtak_counter"
    )

    val MOTTATT_MANUELT_VEDTAK = registry.counter(
        "mottatt_manuelt_vedtak_counter"
    )

    val MOTTATT_AUTOMATISK_VEDTAK = registry.counter(
        "mottatt_automatisk_vedtak_counter"
    )

    val VEDTAK_LEST = registry.counter(
        "vedtak_lest_counter"
    )

    val MOTTATT_ANNULLERING_VEDTAK = registry.counter(
        "mottatt_annullering_vedtak_counter"
    )

    val STATUS_MOTATT = registry.counter(
        "status_motatt_counter"
    )
}
