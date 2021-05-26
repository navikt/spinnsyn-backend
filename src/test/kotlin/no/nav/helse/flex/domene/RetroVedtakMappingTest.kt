package no.nav.helse.flex.domene

import no.nav.helse.flex.vedtak.db.Vedtak
import no.nav.helse.flex.vedtak.domene.Dokument
import no.nav.helse.flex.vedtak.domene.VedtakDto
import no.nav.helse.flex.vedtak.service.tilRSVedtakWrapper
import no.nav.helse.flex.vedtak.service.tilRetroRSVedtak
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import kotlin.random.Random

class RetroVedtakMappingTest {

    val randomVedtakDto = VedtakDto(
        fom = LocalDate.now().minusDays(Random.nextLong(10)),
        tom = LocalDate.now().minusDays(Random.nextLong(10)),
        forbrukteSykedager = Random.nextInt(250),
        gjenståendeSykedager = Random.nextInt(250),
        organisasjonsnummer = UUID.randomUUID().toString(),
        utbetalinger = listOf(
            VedtakDto.UtbetalingDto(
                mottaker = UUID.randomUUID().toString(),
                fagområde = "SPREF",
                totalbeløp = Random.nextInt(20000),
                utbetalingslinjer = listOf(
                    VedtakDto.UtbetalingDto.UtbetalingslinjeDto(
                        fom = LocalDate.now().minusDays(Random.nextLong(10)),
                        tom = LocalDate.now().minusDays(Random.nextLong(10)),
                        dagsats = Random.nextInt(1000),
                        beløp = Random.nextInt(100000),
                        grad = Random.nextDouble(100.0),
                        sykedager = Random.nextInt(100)
                    )
                )
            )
        ),
        dokumenter = listOf(
            Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Søknad),
            Dokument(dokumentId = UUID.randomUUID(), type = Dokument.Type.Sykmelding),
        ),
        automatiskBehandling = Random.nextBoolean()
    )

    val randomVedtak = Vedtak(
        id = UUID.randomUUID().toString(),
        vedtak = randomVedtakDto,
        lest = Random.nextBoolean(),
        lestDato = OffsetDateTime.now().minusDays(Random.nextLong(123)),
        opprettet = OffsetDateTime.now().minusDays(Random.nextLong(123)).toInstant()
    )

    val randomRsVedtak = randomVedtak.tilRetroRSVedtak(Random.nextBoolean())

    @Test
    fun `transformeres riktig frem og tilbake`() {

        val transformert = randomRsVedtak.tilRSVedtakWrapper().tilRetroRSVedtak()

        transformert shouldBeEqualTo randomRsVedtak
    }
}
