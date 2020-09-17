package no.nav.syfo.varsling

import io.ktor.util.KtorExperimentalAPI
import io.mockk.* // ktlint-disable no-wildcard-imports
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.varsling.cronjob.varslingCronjob
import no.nav.syfo.varsling.domene.EnkeltVarsel
import no.nav.syfo.varsling.kafka.EnkeltvarselKafkaProducer
import no.nav.syfo.vedtak.db.* // ktlint-disable no-wildcard-imports
import org.amshove.kluent.`should be equal to`
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.* // ktlint-disable no-wildcard-imports
import java.util.UUID

@KtorExperimentalAPI
object VedtakVarslingSpek : Spek({

    val testDb = TestDB()

    val grunntid = ZonedDateTime.of(2020, 3, 12, 3, 3, 0, 0, ZoneId.of("Europe/Oslo"))
    val enkeltvarselKafkaProducer = mockk<EnkeltvarselKafkaProducer>()

    val fnrSomSkalVarslesOgRevarsles = "01010111111"
    val vedtakIdSomSkalVarslesOgRevarsles = "5fae4253-1e31-4572-ac9c-0e388c22fb6c"

    val fnrSomLeserEtterEttVarsel = "01010111133"
    val vedtakIdSomSkalVarslesOgLeses = "38c26467-e3d9-4b49-8886-35a619138750"

    beforeEachTest {
        every { enkeltvarselKafkaProducer.opprettEnkeltVarsel(any()) } just Runs
    }

    describe("Test vedtak varsling") {
        it("Vi mocker klokka") {
            mockkStatic(Instant::class)
            every {
                Instant.now()
            } returns grunntid.toInstant()
        }

        it("Vi oppretter to vedtak og leser det ene") {

            val fnrSomLeserMedEnGang = "01010111122"
            val idSomLesesMedEnGang = "742d3156-0162-43c7-89bd-3dcfd42e5432"

            testDb.nyttVedtak(fnr = fnrSomSkalVarslesOgRevarsles, id = vedtakIdSomSkalVarslesOgRevarsles)
            testDb.nyttVedtak(fnr = fnrSomLeserMedEnGang, id = idSomLesesMedEnGang)
            testDb.nyttVedtak(fnr = fnrSomLeserEtterEttVarsel, id = vedtakIdSomSkalVarslesOgLeses)

            testDb.lesVedtak(fnr = fnrSomLeserMedEnGang, vedtaksId = idSomLesesMedEnGang)
        }

        it("Vi kjører cronjobben, men klokka er midt på natta") {
            val (varsler, revarsler) = varslingCronjob(testDb, enkeltvarselKafkaProducer)
            varsler `should be equal to` 0
            revarsler `should be equal to` 0
        }

        it("Vi kjører cronjobben på morgenen og varsler 2 stk") {
            every {
                Instant.now()
            } returns grunntid.plusHours(6).toInstant()

            val (varsler, revarsler) = varslingCronjob(testDb, enkeltvarselKafkaProducer)
            varsler `should be equal to` 2
            revarsler `should be equal to` 0

            val enkeltVarsler = mutableListOf<EnkeltVarsel>()
            verify(exactly = 2) { enkeltvarselKafkaProducer.opprettEnkeltVarsel(capture(enkeltVarsler)) }

            enkeltVarsler[0].fodselsnummer `should be equal to` fnrSomSkalVarslesOgRevarsles
            enkeltVarsler[0].varselBestillingId `should be equal to` "8f073468-752f-3c9d-b21f-de0946c07718"
            enkeltVarsler[0].varselTypeId `should be equal to` "NySykmeldingUtenLenke"

            enkeltVarsler[1].fodselsnummer `should be equal to` fnrSomLeserEtterEttVarsel
            enkeltVarsler[1].varselBestillingId `should be equal to` "aa52cdb4-8e61-3655-a0c5-4bef2a544529"
            enkeltVarsler[1].varselTypeId `should be equal to` "NySykmeldingUtenLenke"
            clearMocks(enkeltvarselKafkaProducer, answers = false)
        }

        it("Vi kjører cronjobben igjen, men alle varsler er sendt") {
            val (varsler, revarsler) = varslingCronjob(testDb, enkeltvarselKafkaProducer)
            varsler `should be equal to` 0
            revarsler `should be equal to` 0
        }

        it("Det ene varselet leses") {
            testDb.lesVedtak(fnr = fnrSomLeserEtterEttVarsel, vedtaksId = vedtakIdSomSkalVarslesOgLeses)
        }

        it("Vi kjører cronjobben en uke senere og revarsler 1") {
            every {
                Instant.now()
            } returns grunntid.plusDays(7).plusHours(6).plusMinutes(1).toInstant()
            val (varsler, revarsler) = varslingCronjob(testDb, enkeltvarselKafkaProducer)
            varsler `should be equal to` 0
            revarsler `should be equal to` 1

            val enkeltVarsler = mutableListOf<EnkeltVarsel>()
            verify(exactly = 1) { enkeltvarselKafkaProducer.opprettEnkeltVarsel(capture(enkeltVarsler)) }

            enkeltVarsler[0].fodselsnummer `should be equal to` fnrSomSkalVarslesOgRevarsles
            enkeltVarsler[0].varselBestillingId `should be equal to` "183109de-ebf7-320a-bfd7-4fb7d022f14f"
            enkeltVarsler[0].varselTypeId `should be equal to` "NySykmeldingUtenLenke"
        }

        it("Vi kjører cronjobben igjen og ingenting skjer") {
            val (varsler, revarsler) = varslingCronjob(testDb, enkeltvarselKafkaProducer)
            varsler `should be equal to` 0
            revarsler `should be equal to` 0
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
        vedtak = "{\"vedtak\":123}"
    )
}
