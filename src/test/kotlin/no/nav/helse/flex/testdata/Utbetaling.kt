package no.nav.helse.flex.testdata

import no.nav.helse.flex.domene.UtbetalingUtbetalt
import java.time.LocalDate

fun lagUtbetaling(
    fnr: String,
    org: String,
    fom: LocalDate,
    tom: LocalDate,
    utbetalingId: String,
): UtbetalingUtbetalt =
    UtbetalingUtbetalt(
        fødselsnummer = fnr,
        aktørId = fnr,
        organisasjonsnummer = org,
        fom = fom,
        tom = tom,
        utbetalingId = utbetalingId,
        antallVedtak = 1,
        event = "eventet",
        forbrukteSykedager = 42,
        gjenståendeSykedager = 3254,
        foreløpigBeregnetSluttPåSykepenger = null,
        automatiskBehandling = true,
        arbeidsgiverOppdrag =
            UtbetalingUtbetalt.OppdragDto(
                mottaker = org,
                fagområde = "SP",
                fagsystemId = "1234",
                nettoBeløp = 123,
                utbetalingslinjer = emptyList(),
            ),
        type = "UTBETALING",
        utbetalingsdager =
            listOf(
                UtbetalingUtbetalt.UtbetalingdagDto(
                    dato = fom,
                    type = "AvvistDag",
                    begrunnelser = listOf(UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.MinimumSykdomsgrad),
                ),
            ),
    )
