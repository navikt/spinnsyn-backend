package no.nav.helse.flex.retro

import no.nav.helse.flex.db.Annullering
import no.nav.helse.flex.db.AnnulleringDAO
import no.nav.helse.flex.domene.PeriodeImpl
import no.nav.helse.flex.domene.RSOppdrag
import no.nav.helse.flex.domene.RSUtbetalingUtbetalt
import no.nav.helse.flex.domene.RSUtbetalingslinje
import no.nav.helse.flex.domene.RSVedtak
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.domene.VedtakDto
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
        orgnavn = arbeidsgiveroppdrag.mottaker,
        andreArbeidsgivere = this.vedtak.grunnlagForSykepengegrunnlagPerArbeidsgiver,
        opprettetTimestamp = this.opprettetTimestamp,
        opprettet = this.opprettet,
        vedtak = RSVedtak(
            organisasjonsnummer = arbeidsgiveroppdrag.mottaker,
            dokumenter = this.vedtak.dokumenter,
            sykepengegrunnlag = this.vedtak.sykepengegrunnlag,
            inntekt = this.vedtak.månedsinntekt,
            fom = this.vedtak.fom,
            tom = this.vedtak.tom,
            grunnlagForSykepengegrunnlag = this.vedtak.grunnlagForSykepengegrunnlag,
            grunnlagForSykepengegrunnlagPerArbeidsgiver = this.vedtak.grunnlagForSykepengegrunnlagPerArbeidsgiver,
            begrensning = this.vedtak.begrensning,
            utbetaling = RSUtbetalingUtbetalt(
                organisasjonsnummer = this.vedtak.organisasjonsnummer,
                utbetalingId = null,
                forbrukteSykedager = this.vedtak.forbrukteSykedager,
                gjenståendeSykedager = this.vedtak.gjenståendeSykedager,
                automatiskBehandling = this.vedtak.automatiskBehandling,
                foreløpigBeregnetSluttPåSykepenger = null,
                arbeidsgiverOppdrag = RSOppdrag(
                    mottaker = arbeidsgiveroppdrag.mottaker,
                    nettoBeløp = arbeidsgiveroppdrag.totalbeløp,
                    utbetalingslinjer = arbeidsgiveroppdrag.utbetalingslinjer.map { it.tilRsUtbetalingslinje() }
                ),
                utbetalingsdager = emptyList(),
                utbetalingType = "UTBETALING",
                personOppdrag = null
            )
        )
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
