package no.nav.helse.flex.db

import io.ktor.util.KtorExperimentalAPI
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import no.nav.helse.flex.testutil.TestDB
import no.nav.helse.flex.vedtak.db.hentVedtakForRevarsling
import no.nav.helse.flex.vedtak.db.hentVedtakForVarsling
import no.nav.helse.flex.vedtak.db.lesVedtak
import no.nav.helse.flex.vedtak.db.opprettVedtak
import no.nav.helse.flex.vedtak.db.settVedtakRevarslet
import no.nav.helse.flex.vedtak.db.settVedtakVarslet
import org.amshove.kluent.`should be equal to`
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@KtorExperimentalAPI
object VedtakQueriesSpek : Spek({

    val testDb = TestDB()
    val now = OffsetDateTime.of(2020, 3, 12, 9, 12, 0, 0, ZoneOffset.UTC)

    beforeEachTest {
        testDb.hentVedtakForVarsling().forEach {
            testDb.lesVedtak(it.fnr, it.id)
        }
        testDb.hentVedtakForRevarsling().forEach() {
            testDb.lesVedtak(it.fnr, it.id)
        }
    }

    describe("Test databasefunksjoner") {
        it("Uleste vedtak skal varsles om") {
            mockkStatic(Instant::class)
            every {
                Instant.now()
            } returns now.toInstant()

            repeat((0..4).count()) {
                testDb.nyttVedtak()
            }
            val vedtakForVarsling = testDb.hentVedtakForVarsling()
            vedtakForVarsling.size `should be equal to` 5

            val tilfeldigVedtak = vedtakForVarsling.random()
            testDb.lesVedtak(tilfeldigVedtak.fnr, tilfeldigVedtak.id)
            testDb.hentVedtakForVarsling().size `should be equal to` 4
        }

        it("Et varslet vedtak skal ikke varsles igjen") {
            repeat((0..4).count()) {
                testDb.nyttVedtak()
            }
            val vedtakForVarsling = testDb.hentVedtakForVarsling()
            val tilfeldigVedtak = vedtakForVarsling.random()
            vedtakForVarsling.size `should be equal to` 5

            testDb.settVedtakVarslet(tilfeldigVedtak.id)
            testDb.hentVedtakForVarsling().size `should be equal to` 4
        }

        it("Et uvarslet (og ulest) vedtak kan ikke revarsles før det er varslet") {

            repeat((0..4).count()) {
                testDb.nyttVedtak()
            }
            val vedtakForVarsling = testDb.hentVedtakForVarsling()
            vedtakForVarsling.random().also {
                testDb.settVedtakRevarslet(it.id)
            }
            testDb.hentVedtakForVarsling().size `should be equal to` 5
            testDb.hentVedtakForRevarsling().size `should be equal to` 0

            vedtakForVarsling.forEach {
                testDb.settVedtakVarslet(it.id)
            }

            every {
                Instant.now()
            } returns now.plusDays(8).toInstant()

            testDb.hentVedtakForVarsling().size `should be equal to` 0
            testDb.hentVedtakForRevarsling().size `should be equal to` 6

            testDb.hentVedtakForRevarsling().random().also {
                testDb.settVedtakRevarslet(it.id)
            }
            testDb.hentVedtakForRevarsling().size `should be equal to` 5

            unmockkStatic(Instant::class)
        }
    }
})

private fun DatabaseInterface.nyttVedtak(fnr: String = (0..10).joinToString("") { (0..9).random().toString() }) =
    opprettVedtak(
        id = UUID.randomUUID(),
        fnr = fnr,
        vedtak = "{\"vedtak\":123}"
    )
