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

val VEDTAK_LEST: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("vedtak_lest_counter")
    .help("Antall vedtak lest av brukere")
    .register()