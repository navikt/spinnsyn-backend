package no.nav.helse.flex.datafix

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.serialisertTilString
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class PatchFeilData(
    val leaderElection: LeaderElection,
    val utbetalingRepository: UtbetalingRepository,
) {
    val log = logger()

    @Scheduled(initialDelay = 60 * 5, fixedDelay = 1000L * 60 * 20000, timeUnit = TimeUnit.SECONDS)
    fun run() {
        if (leaderElection.isLeader()) {
            val utbetalingid = "c26d4ec4-fb55-4d8c-b76c-f269f10ca276"
            utbetalingRepository.findByUtbetalingId(utbetalingid)?.let { utbetaling ->
                val utbetalingUtbetalt = utbetaling.utbetaling.tilUtbetalingUtbetalt()
                val oppdatert =
                    utbetalingUtbetalt.copy(utbetalingsdager = utbetalingUtbetalt.utbetalingsdager.fjernUkjent())
                utbetalingRepository.save(utbetaling.copy(utbetaling = oppdatert.serialisertTilString()))
                log.info("Patchet utbetaling med id $utbetalingid og fjerna UKJENT dag ")
            }
        }
    }

    private fun List<UtbetalingUtbetalt.UtbetalingdagDto>.fjernUkjent(): List<UtbetalingUtbetalt.UtbetalingdagDto> =
        this.filterNot { it.begrunnelser.contains(UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.UKJENT) }

    private fun String.tilUtbetalingUtbetalt(): UtbetalingUtbetalt = objectMapper.readValue(this)

    private data class UtbetalingUtbetalt(
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
        val type: String, // UTBETALING, ETTERUTBETALING, ANNULLERING, REVURDERING
        val utbetalingsdager: List<UtbetalingdagDto>
    ) {
        data class OppdragDto(
            val mottaker: String,
            val fagområde: String,
            val fagsystemId: String,
            val nettoBeløp: Int,
            val utbetalingslinjer: List<UtbetalingslinjeDto>
        ) {
            data class UtbetalingslinjeDto(
                val fom: LocalDate,
                val tom: LocalDate,
                val dagsats: Int,
                val totalbeløp: Int,
                val grad: Double,
                val stønadsdager: Int
            )
        }

        data class UtbetalingdagDto(
            val dato: LocalDate,
            val type: String,
            val begrunnelser: List<Begrunnelse>,
        ) {
            @Suppress("unused")
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
                UKJENT,
            }
        }
    }
}
