package no.nav.helse.flex

import no.nav.helse.flex.domene.RSUtbetalingUtbetalt
import no.nav.helse.flex.domene.RSVedtak
import no.nav.helse.flex.domene.RSVedtakWrapper
import java.time.Instant
import java.time.LocalDate

val vedtakTestdata =
    RSVedtakWrapper(
        id = "1",
        lest = false,
        lestDato = null,
        vedtak =
            RSVedtak(
                organisasjonsnummer = "5678",
                yrkesaktivitetstype = "ARBEIDSTAKER",
                fom = LocalDate.now(),
                tom = LocalDate.now(),
                dokumenter = emptyList(),
                utbetaling =
                    RSUtbetalingUtbetalt(
                        utbetalingId = null,
                        organisasjonsnummer = "1234",
                        forbrukteSykedager = 0,
                        gjenståendeSykedager = 0,
                        automatiskBehandling = true,
                        utbetalingsdager = emptyList(),
                        utbetalingType = "",
                        personOppdrag = null,
                        arbeidsgiverOppdrag = null,
                        foreløpigBeregnetSluttPåSykepenger = null,
                    ),
                grunnlagForSykepengegrunnlagPerArbeidsgiver = mapOf("123456547" to 500000.0, "547123456" to 300000.0, "5678" to 100000.0),
                begrensning = null,
                grunnlagForSykepengegrunnlag = null,
                inntekt = null,
                sykepengegrunnlag = null,
                vedtakFattetTidspunkt = LocalDate.now(),
                begrunnelser = null,
                sykepengegrunnlagsfakta = null,
                tags = null,
            ),
        opprettetTimestamp = Instant.now(),
        orgnavn = "1234",
        andreArbeidsgivere = mapOf("123456547" to 500000.0, "547123456" to 300000.0, "5678" to 100000.0),
        annullert = false,
        revurdert = false,
        dagerArbeidsgiver = emptyList(),
        dagerPerson = emptyList(),
        sykepengebelopArbeidsgiver = 0,
        sykepengebelopPerson = 0,
    )
