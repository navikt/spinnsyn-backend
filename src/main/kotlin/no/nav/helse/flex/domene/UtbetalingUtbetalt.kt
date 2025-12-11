package no.nav.helse.flex.domene

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.objectMapper
import java.time.LocalDate

data class UtbetalingUtbetalt(
    val event: String,
    val utbetalingId: String,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val stønadsdager: Int? = null,
    val antallVedtak: Int?,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate?,
    val gjenståendeSykedager: Int,
    val automatiskBehandling: Boolean,
    val arbeidsgiverOppdrag: OppdragDto? = null,
    val personOppdrag: OppdragDto? = null,
    // UTBETALING, ETTERUTBETALING, ANNULLERING, REVURDERING
    val type: String,
    val utbetalingsdager: List<UtbetalingdagDto>,
) {
    data class OppdragDto(
        val mottaker: String,
        val fagområde: String,
        val fagsystemId: String,
        val nettoBeløp: Int,
        val utbetalingslinjer: List<UtbetalingslinjeDto>,
    ) {
        data class UtbetalingslinjeDto(
            val fom: LocalDate,
            val tom: LocalDate,
            val dagsats: Int,
            val totalbeløp: Int,
            val grad: Double,
            val stønadsdager: Int,
        )
    }

    data class UtbetalingdagDto(
        val dato: LocalDate,
        val type: String,
        val begrunnelser: List<Begrunnelse>,
    ) {
        enum class Begrunnelse {
            SykepengedagerOppbrukt,
            SykepengedagerOppbruktOver67,
            MinimumInntekt,
            MinimumInntektOver67,
            EgenmeldingUtenforArbeidsgiverperiode,
            MinimumSykdomsgrad,
            ManglerOpptjening,
            ManglerMedlemskap,
            Over70,
            EtterDødsdato,
            AndreYtelserAap,
            AndreYtelserDagpenger,
            AndreYtelserForeldrepenger,
            AndreYtelserOmsorgspenger,
            AndreYtelserOpplaringspenger,
            AndreYtelserPleiepenger,
            AndreYtelserSvangerskapspenger,
        }
    }
}

fun String.tilUtbetalingUtbetalt(): UtbetalingUtbetalt = objectMapper.readValue(this)
