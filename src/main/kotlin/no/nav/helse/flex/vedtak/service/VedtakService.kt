package no.nav.helse.flex.vedtak.service

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.vedtak.db.*
import no.nav.helse.flex.vedtak.domene.*
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

@Service
class VedtakService(
    private val vedtakRepository: VedtakRepository,
    private val utbetalingRepository: UtbetalingRepository,
    private val retroVedtakService: RetroVedtakService,
    private val environmentToggles: EnvironmentToggles

) {

    fun hentVedtak(fnr: String): List<RSVedtakWrapper> {
        val retroVedtak = retroVedtakService.hentVedtak(fnr)
        if (environmentToggles.isProduction()) {
            return retroVedtak
        }
        val nyeVedtak = hentVedtakFraNyeTabeller(fnr)
        // TODO fjern duplikater på et eller annet vis. Kanskje vi gjør det på vei inn??
        return ArrayList<RSVedtakWrapper>().also {
            it.addAll(retroVedtak)
            it.addAll(nyeVedtak)
        }
    }

    private fun hentVedtakFraNyeTabeller(fnr: String): List<RSVedtakWrapper> {
        val vedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr)
        val utbetalinger = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)

        val eksisterendeUtbetalinger = utbetalinger
            .filter { it.utbetalingType == "UTBETALING" }

        val eksisterendeUtbetalingIder = eksisterendeUtbetalinger
            .map { it.utbetalingId }

        val vedtakMedUtbetaling = vedtak
            .filter { it.utbetalingId != null }
            .filter { eksisterendeUtbetalingIder.contains(it.utbetalingId) }

        fun VedtakDbRecord.tilRsVedtakWrapper(): RSVedtakWrapper {
            val vedtaket = this.vedtak.tilVedtakFattetForEksternDto()
            val utbetaling = utbetalinger
                .find { it.utbetalingId == this.utbetalingId }!!
                .utbetaling
                .tilUtbetalingUtbetalt()

            return RSVedtakWrapper(
                id = this.id!!,
                annullert = false, // TODO håndter annulleringer i den nye
                lest = this.lest != null,
                lestDato = this.lest?.atZone(ZoneId.of("Europe/Oslo"))?.toOffsetDateTime(),
                opprettet = LocalDate.ofInstant(this.opprettet, ZoneId.of("Europe/Oslo")),
                vedtak = RSVedtak(
                    organisasjonsnummer = vedtaket.organisasjonsnummer,
                    dokumenter = vedtaket.dokumenter,
                    sykepengegrunnlag = vedtaket.sykepengegrunnlag,
                    inntekt = vedtaket.inntekt,
                    fom = vedtaket.fom,
                    tom = vedtaket.tom,
                    utbetaling = RSUtbetalingUtbetalt(
                        organisasjonsnummer = utbetaling.organisasjonsnummer,
                        forbrukteSykedager = utbetaling.forbrukteSykedager,
                        gjenståendeSykedager = utbetaling.gjenståendeSykedager,
                        automatiskBehandling = utbetaling.automatiskBehandling,
                        utbetalingsdager = utbetaling.utbetalingsdager.map { it.tilRsUtbetalingsdag() },
                        utbetalingId = utbetaling.utbetalingId,
                        arbeidsgiverOppdrag = RSOppdrag(
                            mottaker = utbetaling.arbeidsgiverOppdrag.mottaker,
                            nettoBeløp = utbetaling.arbeidsgiverOppdrag.nettoBeløp,
                            utbetalingslinjer = utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.map { it.tilRsUtbetalingslinje() }
                        )
                    )
                )
            )
        }

        return vedtakMedUtbetaling.map { it.tilRsVedtakWrapper() }
    }
}

private fun UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto.tilRsUtbetalingslinje(): RSUtbetalingslinje {
    return RSUtbetalingslinje(
        fom = fom, tom = tom, dagsats = dagsats, totalbeløp = totalbeløp, grad = grad, stønadsdager = stønadsdager
    )
}

private fun UtbetalingUtbetalt.UtbetalingdagDto.tilRsUtbetalingsdag(): RSUtbetalingdag {
    return RSUtbetalingdag(
        dato = this.dato,
        type = this.type
    )
}
