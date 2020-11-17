package no.nav.helse.flex.cronjob

import io.ktor.util.KtorExperimentalAPI
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.Environment
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProducer
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.testutil.TestDB
import no.nav.helse.flex.vedtak.cronjob.vedtakCronjob
import no.nav.helse.flex.vedtak.db.finnVedtak
import no.nav.helse.flex.vedtak.db.lesVedtak
import no.nav.helse.flex.vedtak.db.opprettVedtak
import no.nav.helse.flex.vedtak.domene.VedtakDto
import org.amshove.kluent.`should be equal to`
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@KtorExperimentalAPI
object VedtakCronjobSpek : Spek({

    val testDb = TestDB()
    val env = mockk<Environment>()
    val brukernotifikasjonKafkaProducer = mockk<BrukernotifikasjonKafkaProducer>()

    val grunntid = ZonedDateTime.of(2020, 3, 12, 3, 3, 0, 0, ZoneId.of("Europe/Oslo"))

    val fnr = "12345678901"
    val lestVedtak = "742d3156-0162-43c7-89bd-3dcfd42e5432"
    val ulestVedtak = "742d3156-0162-43c7-89bd-3dcfd42e5433"

    describe("Test vedtak sletting") {
        it("Vi mocker") {
            mockkStatic(Instant::class)
            every {
                Instant.now()
            } returns grunntid.toInstant()
            every {
                env.serviceuserUsername
            } returns "srvusr"
            every {
                brukernotifikasjonKafkaProducer.sendDonemelding(any(), any())
            } just Runs
        }

        it("Vi oppretter to vedtak og leser det ene") {
            testDb.nyttVedtak(fnr = fnr, id = lestVedtak)
            testDb.nyttVedtak(fnr = fnr, id = ulestVedtak)

            testDb.lesVedtak(fnr = fnr, vedtaksId = lestVedtak)

            testDb.finnVedtak(fnr).size `should be equal to` 2
        }

        it("Vi kjører cronjobben, men vedtak slettes ikke på dag 365") {
            every {
                Instant.now()
            } returns grunntid.plusDays(365).toInstant()
            val (slettet, doneMelding) = vedtakCronjob(testDb, env, brukernotifikasjonKafkaProducer)
            slettet `should be equal to` 0
            doneMelding `should be equal to` 0
        }

        it("Vi kjører cronjobben senere enn 365 dager, 2 slettes og 1 doneMelding") {
            every {
                Instant.now()
            } returns grunntid.plusDays(365).plusSeconds(1).toInstant()

            val (slettet, doneMelding) = vedtakCronjob(testDb, env, brukernotifikasjonKafkaProducer)
            slettet `should be equal to` 2
            doneMelding `should be equal to` 1

            val nokkel = mutableListOf<Nokkel>()
            val done = mutableListOf<Done>()
            verify(exactly = 1) { brukernotifikasjonKafkaProducer.sendDonemelding(capture(nokkel), capture(done)) }
            nokkel[0].getSystembruker() `should be equal to` "srvusr"
            nokkel[0].getEventId() `should be equal to` ulestVedtak
            done[0].getTidspunkt() `should be equal to` Instant.now().toEpochMilli()
            done[0].getFodselsnummer() `should be equal to` fnr
            done[0].getGrupperingsId() `should be equal to` ulestVedtak

            testDb.finnVedtak(fnr).size `should be equal to` 0
        }

        it("Vi kjører cronjobben igjen og ingenting skjer") {
            every {
                Instant.now()
            } returns grunntid.plusDays(365).plusSeconds(1).toInstant()
            val (slettet, doneMelding) = vedtakCronjob(testDb, env, brukernotifikasjonKafkaProducer)
            slettet `should be equal to` 0
            doneMelding `should be equal to` 0
        }

        it("Vi avmocker klokka") {
            unmockkStatic(Instant::class)
        }
    }
})

private fun DatabaseInterface.nyttVedtak(
    fnr: String,
    id: String
) {
    opprettVedtak(
        id = UUID.fromString(id),
        fnr = fnr,
        lest = false,
        opprettet = Instant.now(),
        vedtak = VedtakDto(
            fom = LocalDate.now(),
            tom = LocalDate.now(),
            forbrukteSykedager = 1,
            gjenståendeSykedager = 2,
            utbetalinger = emptyList(),
            dokumenter = emptyList(),
            automatiskBehandling = true
        ).serialisertTilString()
    )
}
