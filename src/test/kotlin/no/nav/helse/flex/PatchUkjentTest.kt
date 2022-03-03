package no.nav.helse.flex

import no.nav.helse.flex.datafix.PatchFeilData
import no.nav.helse.flex.db.UtbetalingDbRecord
import no.nav.helse.flex.domene.*
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

class PatchUkjentTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var patchFeilData: PatchFeilData

    final val fnr = "1233342"
    final val aktørId = "321"
    final val org = "987123123"
    final val now = LocalDate.now()
    final val utbetalingId = "c26d4ec4-fb55-4d8c-b76c-f269f10ca276"

    @Test
    fun `patcher feil data`() {
        val utbetalingFoer = UtbetalingUtbetalt(
            fødselsnummer = fnr,
            aktørId = aktørId,
            organisasjonsnummer = org,
            fom = now,
            tom = now,
            utbetalingId = utbetalingId,
            antallVedtak = 1,
            event = "eventet",
            forbrukteSykedager = 42,
            gjenståendeSykedager = 3254,
            foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2020, 3, 12),
            automatiskBehandling = true,
            arbeidsgiverOppdrag = UtbetalingUtbetalt.OppdragDto(
                mottaker = org,
                fagområde = "SP",
                fagsystemId = "1234",
                nettoBeløp = 123,
                utbetalingslinjer = emptyList()
            ),
            type = "UTBETALING",
            utbetalingsdager = listOf(
                UtbetalingUtbetalt.UtbetalingdagDto(
                    dato = now,
                    type = "AvvistDag",
                    begrunnelser = listOf(UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.MinimumSykdomsgrad)
                ),
                UtbetalingUtbetalt.UtbetalingdagDto(
                    dato = now,
                    type = "AvvistDag",
                    begrunnelser = listOf(UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.EtterDødsdato)
                )
            )
        )

        utbetalingFoer.utbetalingsdager shouldHaveSize 2

        utbetalingRepository.save(
            UtbetalingDbRecord(
                fnr = fnr,
                utbetalingType = utbetalingFoer.type,
                utbetaling = utbetalingFoer.serialisertTilString().replaceFirst("MinimumSykdomsgrad", "UKJENT"),
                opprettet = Instant.now(),
                utbetalingId = utbetalingFoer.utbetalingId,
                antallVedtak = utbetalingFoer.antallVedtak!!
            )
        )

        patchFeilData.run()

        val utbetalingEtter = utbetalingRepository.findByUtbetalingId(utbetalingId)!!
        utbetalingEtter.utbetaling.tilUtbetalingUtbetalt().utbetalingsdager shouldHaveSize 1
        utbetalingEtter.utbetaling.tilUtbetalingUtbetalt().utbetalingsdager.first().begrunnelser.first().`should be equal to`(UtbetalingUtbetalt.UtbetalingdagDto.Begrunnelse.EtterDødsdato)
    }
}
