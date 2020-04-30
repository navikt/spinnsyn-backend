package no.nav.syfo.application.vedtak.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class Utbetaling (
    val aktørId: String,
    val fødselsnummer: String,
    val førsteFraværsdag: LocalDate,
    val hendelser: Set<UUID>,
    val utbetalingslinjer: List<Utbetalingslinje>,
    val forbrukteSykedager: Int,
    val dagerIgjen: Int,
    val opprettet: LocalDateTime
)