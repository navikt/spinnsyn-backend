package no.nav.helse.flex.service

import no.nav.helse.flex.db.*
import no.nav.helse.flex.domene.*
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

@Service
class VedtakService(
    private val vedtakRepository: VedtakRepository,
    private val utbetalingRepository: UtbetalingRepository,
    private val retroVedtakService: RetroVedtakService,
    private val annulleringDAO: AnnulleringDAO

) {

    fun hentVedtak(fnr: String): List<RSVedtakWrapper> {
        val retroVedtak = retroVedtakService.hentVedtak(fnr)

        val nyeVedtak = hentVedtakFraNyeTabeller(fnr)

        return ArrayList<RSVedtakWrapper>().also {
            it.addAll(retroVedtak)
            it.addAll(nyeVedtak)
        }
    }

    fun hentVedtakFraNyeTabeller(fnr: String): List<RSVedtakWrapper> {
        val vedtak = vedtakRepository.findVedtakDbRecordsByFnr(fnr)
        val utbetalinger = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
        val annulleringer = annulleringDAO.finnAnnullering(fnr)

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
                annullert = annulleringer.annullererVedtak(vedtaket),
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

    fun hentRetroVedtak(fnr: String): List<RetroRSVedtak> {
        return this.hentVedtak(fnr).map {
            it.tilRetroRSVedtak()
        }
    }
}

private fun List<Annullering>.annullererVedtak(vedtakDbRecord: VedtakFattetForEksternDto): Boolean {
    return this.any {
        vedtakDbRecord.matcherAnnullering(it)
    }
}

fun VedtakFattetForEksternDto.matcherAnnullering(annullering: Annullering): Boolean {
    val vedtaksperiode = Periode(this.fom, this.tom)
    val annulleringsperiode = Periode(
        annullering.annullering.fom ?: return false,
        annullering.annullering.tom
            ?: return false
    )
    return vedtaksperiode.overlapper(annulleringsperiode) && (this.organisasjonsnummer == annullering.annullering.orgnummer)
}

private fun UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto.tilRsUtbetalingslinje(): RSUtbetalingslinje {
    return RSUtbetalingslinje(
        fom = fom,
        tom = tom,
        dagsats = dagsats,
        totalbeløp = totalbeløp,
        grad = grad,
        stønadsdager = stønadsdager,
        dagsatsTransformasjonHjelper = dagsats
    )
}

private fun UtbetalingUtbetalt.UtbetalingdagDto.tilRsUtbetalingsdag(): RSUtbetalingdag {
    return RSUtbetalingdag(
        dato = this.dato,
        type = this.type
    )
}
