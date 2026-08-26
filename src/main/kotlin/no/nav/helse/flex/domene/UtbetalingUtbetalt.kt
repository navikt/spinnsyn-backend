package no.nav.helse.flex.domene

import no.nav.helse.flex.objectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

// Defaultene på de primitive non-null feltene gjør at oppførsel fra Jackson 2 videreføres siden
// Jackson 3 feiler på manglende felter.
data class UtbetalingUtbetalt(
    val event: String,
    val utbetalingId: String,
    val fødselsnummer: String,
    val aktørId: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int = 0,
    val stønadsdager: Int? = null,
    val antallVedtak: Int?,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate?,
    val gjenståendeSykedager: Int = 0,
    val automatiskBehandling: Boolean = false,
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
        val nettoBeløp: Int = 0,
        val utbetalingslinjer: List<UtbetalingslinjeDto>,
    ) {
        data class UtbetalingslinjeDto(
            val fom: LocalDate,
            val tom: LocalDate,
            val dagsats: Int = 0,
            val totalbeløp: Int = 0,
            val grad: Double = 0.0,
            val stønadsdager: Int = 0,
        )
    }

    data class UtbetalingdagDto(
        val dato: LocalDate,
        val type: String,
        val begrunnelser: List<Begrunnelse>,
        val beløpTilArbeidsgiver: Int? = null,
        val beløpTilSykmeldt: Int? = null,
        val sykdomsgrad: Int? = null,
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
            AvslattMeldingTilNavDag,
        }
    }
}

fun String.tilUtbetalingUtbetalt(): UtbetalingUtbetalt = objectMapper.readValue(this)
