package no.nav.syfo.db

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.vedtak.db.hentVedtakForRevarsling
import no.nav.syfo.vedtak.db.hentVedtakForVarsling
import no.nav.syfo.vedtak.db.lesVedtak
import no.nav.syfo.vedtak.db.opprettVedtak
import no.nav.syfo.vedtak.db.settVedtakRevarslet
import no.nav.syfo.vedtak.db.settVedtakVarslet
import org.amshove.kluent.`should be equal to`
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

@KtorExperimentalAPI
object VedtakVerdikjedeSpek : Spek({

    val testDb = TestDB()

    beforeEachTest {
        testDb.hentVedtakForVarsling().forEach {
            testDb.lesVedtak(it.fnr, it.id)
        }
        testDb.hentVedtakForRevarsling(0).forEach() {
            testDb.lesVedtak(it.fnr, it.id)
        }
    }

    describe("Test databasefunksjoner") {
        it("Uleste vedtak skal varsles om") {
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
            testDb.hentVedtakForRevarsling(0).size `should be equal to` 0

            vedtakForVarsling.forEach {
                testDb.settVedtakVarslet(it.id)
            }
            testDb.hentVedtakForVarsling().size `should be equal to` 0
            testDb.hentVedtakForRevarsling(0).size `should be equal to` 5

            testDb.hentVedtakForRevarsling(0).random().also {
                testDb.settVedtakRevarslet(it.id)
            }
            testDb.hentVedtakForRevarsling(0).size `should be equal to` 4
        }
    }
})

private fun DatabaseInterface.nyttVedtak(fnr: String = (0..10).joinToString("") { (0..9).random().toString() }) =
    opprettVedtak(
        id = UUID.randomUUID(),
        fnr = fnr,
        vedtak = "{\"vedtak\":123}"
    )
