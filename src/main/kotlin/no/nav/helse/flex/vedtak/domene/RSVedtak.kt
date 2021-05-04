package no.nav.helse.flex.vedtak.domene

import java.time.LocalDate
import java.time.OffsetDateTime

data class RSVedtakWrapper(
    val id: String,
    val lest: Boolean,
    val lestDato: OffsetDateTime? = null,
    val vedtak: RSVedtak,
    val opprettet: LocalDate,
    val annullert: Boolean = false
)

data class RSVedtak(
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val dokumenter: List<Dokument>,
    val inntekt: Double?,
    val sykepengegrunnlag: Double?,
    val utbetaling: RSUtbetalingUtbetalt,
)

data class RSUtbetalingUtbetalt(
    val organisasjonsnummer: String,
    val utbetalingId: String?,
    val forbrukteSykedager: Int,
    val gjenståendeSykedager: Int,
    val automatiskBehandling: Boolean,
    val arbeidsgiverOppdrag: RSOppdrag,
    val utbetalingsdager: List<RSUtbetalingdag>,
)

data class RSOppdrag(
    val mottaker: String,
    val nettoBeløp: Int,
    val utbetalingslinjer: List<RSUtbetalingslinje>,
)

data class RSUtbetalingslinje(
    val fom: LocalDate,
    val tom: LocalDate,
    val dagsats: Int,
    val totalbeløp: Int,
    val grad: Double,
)

data class RSUtbetalingdag(
    val dato: LocalDate,
    val type: String,
)
