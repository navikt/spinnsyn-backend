package no.nav.syfo.application.utbetaling.model

import java.time.LocalDate

data class Utbetalingslinje(
    val fom: LocalDate,
    val tom: LocalDate,
    val dagsats: Int,
    val beløp: Int,
    val grad: Double,
    val enDelAvPeriode: Boolean,
    val mottaker: String,
    val konto: String
)
