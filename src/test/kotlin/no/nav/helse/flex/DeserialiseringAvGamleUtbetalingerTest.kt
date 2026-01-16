package no.nav.helse.flex

import no.nav.helse.flex.domene.tilUtbetalingUtbetalt
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test

/**
 * Denne testen verifiserer at gamle utbetalinger fra databasen uten de nye feltene
 * (beløpTilArbeidsgiver, beløpTilSykmeldt, sykdomsgrad) kan deserialiseres uten feil.
 */
class DeserialiseringAvGamleUtbetalingerTest {
    @Test
    fun `kan deserialisere gammel utbetaling uten nye felter`() {
        val gammelUtbetalingJson =
            """
            {
              "event": "utbetaling_utbetalt",
              "utbetalingId": "test-123",
              "fødselsnummer": "12345678910",
              "aktørId": "1234567891234",
              "organisasjonsnummer": "123456789",
              "fom": "2023-01-01",
              "tom": "2023-01-31",
              "forbrukteSykedager": 10,
              "gjenståendeSykedager": 238,
              "automatiskBehandling": true,
              "type": "UTBETALING",
              "antallVedtak": 1,
              "foreløpigBeregnetSluttPåSykepenger": "2023-12-31",
              "utbetalingsdager": [
                {
                  "dato": "2023-01-17",
                  "type": "NavDag",
                  "begrunnelser": []
                },
                {
                  "dato": "2023-01-18",
                  "type": "NavDag",
                  "begrunnelser": []
                },
                {
                  "dato": "2023-01-19",
                  "type": "AvvistDag",
                  "begrunnelser": ["MinimumSykdomsgrad"]
                }
              ]
            }
            """.trimIndent()

        val utbetaling = gammelUtbetalingJson.tilUtbetalingUtbetalt()

        utbetaling.shouldNotBeNull()
        utbetaling.utbetalingId.shouldBeEqualTo("test-123")
        utbetaling.utbetalingsdager.size.shouldBeEqualTo(3)

        utbetaling.utbetalingsdager[0].beløpTilArbeidsgiver.shouldBeEqualTo(null)
        utbetaling.utbetalingsdager[0].beløpTilSykmeldt.shouldBeEqualTo(null)
        utbetaling.utbetalingsdager[0].sykdomsgrad.shouldBeEqualTo(null)
    }

    @Test
    fun `kan deserialisere ny utbetaling med nye felter`() {
        val nyUtbetalingJson =
            """
            {
              "event": "utbetaling_utbetalt",
              "utbetalingId": "test-456",
              "fødselsnummer": "12345678910",
              "aktørId": "1234567891234",
              "organisasjonsnummer": "123456789",
              "fom": "2023-01-01",
              "tom": "2023-01-31",
              "forbrukteSykedager": 10,
              "gjenståendeSykedager": 238,
              "automatiskBehandling": true,
              "type": "UTBETALING",
              "antallVedtak": 1,
              "foreløpigBeregnetSluttPåSykepenger": "2023-12-31",
              "utbetalingsdager": [
                {
                  "dato": "2023-01-17",
                  "type": "NavDag",
                  "begrunnelser": [],
                  "beløpTilArbeidsgiver": 1000,
                  "beløpTilSykmeldt": 500,
                  "sykdomsgrad": 100
                },
                {
                  "dato": "2023-01-18",
                  "type": "NavDag",
                  "begrunnelser": [],
                  "beløpTilArbeidsgiver": 800,
                  "beløpTilSykmeldt": 200,
                  "sykdomsgrad": 75
                }
              ]
            }
            """.trimIndent()

        val utbetaling = nyUtbetalingJson.tilUtbetalingUtbetalt()

        utbetaling.shouldNotBeNull()
        utbetaling.utbetalingId.shouldBeEqualTo("test-456")

        utbetaling.utbetalingsdager[0].beløpTilArbeidsgiver.shouldBeEqualTo(1000)
        utbetaling.utbetalingsdager[0].beløpTilSykmeldt.shouldBeEqualTo(500)
        utbetaling.utbetalingsdager[0].sykdomsgrad.shouldBeEqualTo(100)

        utbetaling.utbetalingsdager[1].beløpTilArbeidsgiver.shouldBeEqualTo(800)
        utbetaling.utbetalingsdager[1].beløpTilSykmeldt.shouldBeEqualTo(200)
        utbetaling.utbetalingsdager[1].sykdomsgrad.shouldBeEqualTo(75)
    }
}
