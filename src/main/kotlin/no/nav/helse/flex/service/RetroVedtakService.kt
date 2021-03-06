package no.nav.helse.flex.service

import no.nav.helse.flex.db.Annullering
import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.db.Vedtak
import no.nav.helse.flex.db.VedtakDAO
import no.nav.helse.flex.domene.*
import no.nav.helse.flex.logger
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class RetroVedtakService(
    private val vedtakDAO: VedtakDAO,
    private val annulleringDAO: AnnulleringDAO,
) {
    private val log = logger()

    fun hentVedtak(fnr: String): List<RSVedtakWrapper> {
        return hentRetroVedtak(fnr)
            .filter { rsVedtak ->
                if (rsVedtak.vedtak.utbetalinger.find { it.fagområde == "SPREF" } == null) {
                    log.warn("Forventet at vedtak ${rsVedtak.id} har SPREF utbetaling")
                    false
                } else {
                    true
                }
            }
            .map { it.tilRSVedtakWrapper() }
    }

    fun hentRetroVedtak(fnr: String): List<RetroRSVedtak> {
        val annulleringer = annulleringDAO.finnAnnullering(fnr)
        val finnVedtak = vedtakDAO.finnVedtak(fnr)
        return finnVedtak
            .map { it.tilRetroRSVedtak(annulleringer.forVedtak(it)) }
    }
}

data class RetroRSVedtak(
    val id: String,
    val lest: Boolean,
    val lestDato: OffsetDateTime? = null,
    val vedtak: VedtakDto,
    val opprettet: LocalDate,
    val opprettetTimestamp: Instant,
    val annullert: Boolean = false
)

fun Vedtak.tilRetroRSVedtak(annullering: Boolean = false): RetroRSVedtak {
    return RetroRSVedtak(
        id = this.id,
        lest = this.lest,
        lestDato = this.lestDato,
        vedtak = this.vedtak,
        opprettet = LocalDate.ofInstant(this.opprettet, ZoneId.of("Europe/Oslo")),
        opprettetTimestamp = this.opprettet,
        annullert = annullering
    )
}

fun RetroRSVedtak.tilRSVedtakWrapper(): RSVedtakWrapper {
    val arbeidsgiveroppdrag = this.vedtak.utbetalinger.first { it.fagområde == "SPREF" }
    return RSVedtakWrapper(
        id = this.id,
        annullert = this.annullert,
        lest = this.lest,
        lestDato = this.lestDato,
        opprettetTimestamp = this.opprettetTimestamp,
        opprettet = this.opprettet,
        vedtak = RSVedtak(
            organisasjonsnummer = this.vedtak.organisasjonsnummer,
            dokumenter = this.vedtak.dokumenter,
            sykepengegrunnlag = this.vedtak.sykepengegrunnlag,
            inntekt = this.vedtak.månedsinntekt,
            fom = this.vedtak.fom,
            tom = this.vedtak.tom,
            utbetaling = RSUtbetalingUtbetalt(
                organisasjonsnummer = this.vedtak.organisasjonsnummer,
                utbetalingId = null,
                forbrukteSykedager = this.vedtak.forbrukteSykedager,
                gjenståendeSykedager = this.vedtak.gjenståendeSykedager,
                automatiskBehandling = this.vedtak.automatiskBehandling,
                arbeidsgiverOppdrag = RSOppdrag(
                    mottaker = arbeidsgiveroppdrag.mottaker,
                    nettoBeløp = arbeidsgiveroppdrag.totalbeløp,
                    utbetalingslinjer = arbeidsgiveroppdrag.utbetalingslinjer.map { it.tilRsUtbetalingslinje() }
                ),
                utbetalingsdager = emptyList(),
                utbetalingType = "UTBETALING"
            )
        )
    )
}

fun RSVedtakWrapper.tilRetroRSVedtak(): RetroRSVedtak {
    return RetroRSVedtak(
        id = this.id,
        annullert = this.annullert,
        lest = this.lest,
        lestDato = this.lestDato,
        opprettet = this.opprettet,
        opprettetTimestamp = this.opprettetTimestamp,
        vedtak = VedtakDto(
            automatiskBehandling = this.vedtak.utbetaling.automatiskBehandling,
            organisasjonsnummer = this.vedtak.organisasjonsnummer,
            dokumenter = this.vedtak.dokumenter,
            sykepengegrunnlag = this.vedtak.sykepengegrunnlag,
            månedsinntekt = this.vedtak.inntekt,
            fom = this.vedtak.fom,
            tom = this.vedtak.tom,
            forbrukteSykedager = this.vedtak.utbetaling.forbrukteSykedager,
            gjenståendeSykedager = this.vedtak.utbetaling.gjenståendeSykedager,
            utbetalinger = listOf(
                VedtakDto.UtbetalingDto(
                    mottaker = this.vedtak.utbetaling.arbeidsgiverOppdrag.mottaker,
                    fagområde = "SPREF",
                    totalbeløp = this.vedtak.utbetaling.arbeidsgiverOppdrag.nettoBeløp,
                    utbetalingslinjer = this.vedtak.utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.map { it.tilUtbetalingslinjeDto() }
                )
            )
        )
    )
}

private fun RSUtbetalingslinje.tilUtbetalingslinjeDto(): VedtakDto.UtbetalingDto.UtbetalingslinjeDto {
    return VedtakDto.UtbetalingDto.UtbetalingslinjeDto(
        beløp = this.dagsats,
        fom = this.fom,
        tom = this.tom,
        grad = this.grad,
        dagsats = this.dagsatsTransformasjonHjelper,
        sykedager = this.stønadsdager,
    )
}

private fun VedtakDto.UtbetalingDto.UtbetalingslinjeDto.tilRsUtbetalingslinje(): RSUtbetalingslinje {
    return RSUtbetalingslinje(
        dagsats = this.beløp,
        dagsatsTransformasjonHjelper = this.dagsats,
        fom = this.fom,
        tom = this.tom,
        grad = this.grad,
        totalbeløp = this.beløp * this.sykedager,
        stønadsdager = this.sykedager
    )
}

fun List<Annullering>.forVedtak(vedtak: Vedtak): Boolean =
    this.any {
        vedtak.matcherAnnullering(it)
    }

fun Vedtak.matcherAnnullering(annullering: Annullering): Boolean {
    val vedtaksperiode = PeriodeImpl(this.vedtak.fom, this.vedtak.tom)
    val annulleringsperiode = PeriodeImpl(
        annullering.annullering.fom ?: return false,
        annullering.annullering.tom
            ?: return false
    )
    return vedtaksperiode.overlapper(annulleringsperiode) &&
        (
            this.vedtak.organisasjonsnummer == annullering.annullering.orgnummer ||
                this.vedtak.utbetalinger.any { it.mottaker == annullering.annullering.orgnummer }
            )
}
