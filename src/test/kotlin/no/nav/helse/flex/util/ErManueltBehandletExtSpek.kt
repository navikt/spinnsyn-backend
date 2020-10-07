package no.nav.helse.flex.util

import io.ktor.util.KtorExperimentalAPI
import org.amshove.kluent.`should be`
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object ErManueltBehandletExtSpek : Spek({

    describe("Test manuelt vedtak extensions") {
        it("Automatisk true") {
            val vedtak =
                """
            {
                "automatiskBehandling": true,
                "fom":"2020-09-23",
                "tom":"2020-09-23",
                "forbrukteSykedager":9,
                "gjenståendeSykedager":9,
                "utbetalinger":[],
                "dokumenter":[]
            } 
            """.trimMargin()

            vedtak.erManueltBehandlet() `should be` false
            vedtak.erAutomatiskBehandlet() `should be` true
        }

        it("Automatisk false") {
            val vedtak =
                """
            {
                "automatiskBehandling": false,
                "fom":"2020-09-23",
                "tom":"2020-09-23",
                "forbrukteSykedager":9,
                "gjenståendeSykedager":9,
                "utbetalinger":[],
                "dokumenter":[]
            } 
            """.trimMargin()

            vedtak.erManueltBehandlet() `should be` true
            vedtak.erAutomatiskBehandlet() `should be` false
        }

        it("Ingen automatisk info tolkes til manuelt") {
            val vedtak =
                """
            {
                "fom":"2020-09-23",
                "tom":"2020-09-23",
                "forbrukteSykedager":9,
                "gjenståendeSykedager":9,
                "utbetalinger":[],
                "dokumenter":[]
            } 
            """.trimMargin()

            vedtak.erManueltBehandlet() `should be` true
            vedtak.erAutomatiskBehandlet() `should be` false
        }
    }
})
