package no.nav.syfo.utbetaling.db

import no.nav.syfo.application.utbetaling.db.registrerUtbetaling
import no.nav.syfo.application.utbetaling.model.Utbetaling
import no.nav.syfo.utbetaling.util.TestDB
import no.nav.syfo.utbetaling.util.dropData
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class UtbetalingDAOTest : Spek({

    val testDB = TestDB()

    afterEachTest {
        testDB.connection.dropData()
    }

    afterGroup {
        testDB.stop()
    }

    describe("UtbetalingDAO test") {
        val utbetaling = Utbetaling(
            id = UUID.randomUUID(),
            aktørId = "12345678912",
            fødselsnummer = "12345678912"
        )

        it("Should insert") {
            testDB.registrerUtbetaling(utbetaling)
        }
    }
})
