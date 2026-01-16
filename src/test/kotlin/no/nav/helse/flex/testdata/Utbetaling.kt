package no.nav.helse.flex.testdata

import no.nav.helse.flex.domene.UtbetalingUtbetalt
import java.time.LocalDate

fun lagUtbetaling(
    event: String = "eventet",
    utbetalingId: String = "utbetalingId",
    fødselsnummer: String = "fnr",
    aktørId: String = "fnr",
    organisasjonsnummer: String = "org",
    fom: LocalDate,
    tom: LocalDate,
    forbrukteSykedager: Int = 42,
    stønadsdager: Int? = null,
    antallVedtak: Int? = 1,
    foreløpigBeregnetSluttPåSykepenger: LocalDate? = null,
    gjenståendeSykedager: Int = 3254,
    automatiskBehandling: Boolean = true,
    arbeidsgiverOppdrag: UtbetalingUtbetalt.OppdragDto? =
        UtbetalingUtbetalt.OppdragDto(
            mottaker = organisasjonsnummer,
            fagområde = "SP",
            fagsystemId = "1234",
            nettoBeløp = 123,
            utbetalingslinjer = emptyList(),
        ),
    personOppdrag: UtbetalingUtbetalt.OppdragDto? = null,
    type: String = "UTBETALING",
    utbetalingsdager: List<UtbetalingUtbetalt.UtbetalingdagDto> =
        listOf(
            UtbetalingUtbetalt.UtbetalingdagDto(
                dato = fom,
                type = "AvvistDag",
                begrunnelser = listOf(UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.MinimumSykdomsgrad),
            ),
        ),
): UtbetalingUtbetalt =
    UtbetalingUtbetalt(
        event = event,
        utbetalingId = utbetalingId,
        fødselsnummer = fødselsnummer,
        aktørId = aktørId,
        organisasjonsnummer = organisasjonsnummer,
        fom = fom,
        tom = tom,
        forbrukteSykedager = forbrukteSykedager,
        stønadsdager = stønadsdager,
        antallVedtak = antallVedtak,
        foreløpigBeregnetSluttPåSykepenger = foreløpigBeregnetSluttPåSykepenger,
        gjenståendeSykedager = gjenståendeSykedager,
        automatiskBehandling = automatiskBehandling,
        arbeidsgiverOppdrag = arbeidsgiverOppdrag,
        personOppdrag = personOppdrag,
        type = type,
        utbetalingsdager = utbetalingsdager,
    )
