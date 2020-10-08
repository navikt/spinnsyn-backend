package no.nav.helse.flex.application.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = ""

val HTTP_HISTOGRAM: Histogram = Histogram.Builder()
    .labelNames("path")
    .name("requests_duration_seconds")
    .help("http requests durations for incoming requests in seconds")
    .register()

val MOTTATT_VEDTAK: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("mottatt_vedtak_counter")
    .help("Antall mottatte vedtak")
    .register()

val MOTTATT_MANUELT_VEDTAK: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("mottatt_manuelt_vedtak_counter")
    .help("Antall mottatte manuelle vedtak")
    .register()

val MOTTATT_AUTOMATISK_VEDTAK: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("mottatt_automatisk_vedtak_counter")
    .help("Antall mottatte automatiske vedtak")
    .register()

val VEDTAK_LEST: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("vedtak_lest_counter")
    .help("Antall vedtak lest av brukere")
    .register()

val FØRSTEGANGSVARSEL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("forstegangsvarsel_counter")
    .help("Antall førstegangsvarsler")
    .register()

val REVARSEL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("revarsel_counter")
    .help("Antall revarsler")
    .register()
