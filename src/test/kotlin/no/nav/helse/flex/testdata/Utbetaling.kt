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
    gjenståendeSykedager: Int = 3254,
    utbetalingsdager: List<UtbetalingUtbetalt.UtbetalingdagDto> = listOf(lagUtbetalingdag(dato = fom)),
    type: String = "UTBETALING",
    stønadsdager: Int? = null,
    antallVedtak: Int? = 1,
    foreløpigBeregnetSluttPåSykepenger: LocalDate? = null,
    automatiskBehandling: Boolean = true,
    arbeidsgiverOppdrag: UtbetalingUtbetalt.OppdragDto? = null,
    personOppdrag: UtbetalingUtbetalt.OppdragDto? = null,
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

fun lagArbeidsgiverOppdrag(
    mottaker: String = "org",
    fagområde: String = "SP",
    fagsystemId: String = "1234",
    nettoBeløp: Int = 123,
    utbetalingslinjer: List<UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto> = emptyList(),
): UtbetalingUtbetalt.OppdragDto =
    UtbetalingUtbetalt.OppdragDto(
        mottaker = mottaker,
        fagområde = fagområde,
        fagsystemId = fagsystemId,
        nettoBeløp = nettoBeløp,
        utbetalingslinjer = utbetalingslinjer,
    )

fun lagPersonOppdrag(
    mottaker: String = "fnr",
    fagområde: String = "SP",
    fagsystemId: String = "1234",
    nettoBeløp: Int = 123,
    utbetalingslinjer: List<UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto> = emptyList(),
): UtbetalingUtbetalt.OppdragDto =
    UtbetalingUtbetalt.OppdragDto(
        mottaker = mottaker,
        fagområde = fagområde,
        fagsystemId = fagsystemId,
        nettoBeløp = nettoBeløp,
        utbetalingslinjer = utbetalingslinjer,
    )

fun lagUtbetalingslinje(
    fom: LocalDate,
    tom: LocalDate,
    dagsats: Int = 123,
    totalbeløp: Int = 738,
    grad: Double = 100.0,
    stønadsdager: Int = 6,
): UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto =
    UtbetalingUtbetalt.OppdragDto.UtbetalingslinjeDto(
        fom = fom,
        tom = tom,
        dagsats = dagsats,
        totalbeløp = totalbeløp,
        grad = grad,
        stønadsdager = stønadsdager,
    )

fun lagUtbetalingdag(
    dato: LocalDate,
    type: String = "AvvistDag",
    begrunnelser: List<UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse> = emptyList(),
    beløpTilArbeidsgiver: Int = 123,
    beløpTilSykmeldt: Int = 123,
    sykdomsgrad: Int = 100,
): UtbetalingUtbetalt.UtbetalingdagDto =
    UtbetalingUtbetalt.UtbetalingdagDto(
        dato = dato,
        type = type,
        begrunnelser = begrunnelser,
        beløpTilArbeidsgiver = beløpTilArbeidsgiver,
        beløpTilSykmeldt = beløpTilSykmeldt,
        sykdomsgrad = sykdomsgrad,
    )
