package no.nav.helse.flex

import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.service.MottaUtbetalingService
import no.nav.helse.flex.service.VedtakStatusService
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.random.Random.Default.nextInt

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class UtbetalingMotattPublisert : AbstractContainerBaseTest() {
    @Autowired
    private lateinit var mottaUtbetalingService: MottaUtbetalingService

    @Autowired
    private lateinit var vedtakStatusService: VedtakStatusService

    private val fnr = "9834593845"
    private val aktørId = "9834593845"

    private fun randomUtbetaling(): String {
        val random = nextInt(100000000, 1000000000)
        val org = random.toString()

        return UtbetalingUtbetalt(
            fødselsnummer = fnr,
            aktørId = aktørId,
            organisasjonsnummer = org,
            fom = LocalDate.of(2021, 1, random.mod(27).plus(1)),
            tom = LocalDate.of(2021, 2, random.mod(27).plus(1)),
            utbetalingId = UUID.randomUUID().toString(),
            event = "eventet",
            antallVedtak = 1,
            forbrukteSykedager = 42,
            foreløpigBeregnetSluttPåSykepenger = null,
            gjenståendeSykedager = 254,
            automatiskBehandling = random.mod(2) == 0,
            arbeidsgiverOppdrag = UtbetalingUtbetalt.OppdragDto(
                mottaker = org,
                fagområde = "SPREF",
                fagsystemId = UUID.randomUUID().toString(),
                nettoBeløp = random % 10000,
                utbetalingslinjer = emptyList()
            ),
            type = "UTBETALING",
            utbetalingsdager = emptyList(),
        ).serialisertTilString()
    }

    @Test
    @Order(0)
    fun `fyll databasen med utbetalinger`() {
        generateSequence { randomUtbetaling() }
            .take(1000)
            .forEach {
                mottaUtbetalingService.mottaUtbetaling(fnr, it, Instant.now())
            }
    }

    @Test
    @Order(1)
    fun `alle utbetalinger mangler motatt publisert`() {
        utbetalingRepository
            .findUtbetalingDbRecordsByFnr(fnr)
            .forEach {
                it.motattPublisert.shouldBeNull()
            }
    }

    @Test
    @Order(2)
    fun `oppdaterer motatt publisert`() {
        vedtakStatusService.settMotattPulisertTilNå()
    }

    @Test
    @Order(3)
    fun `alle utbetalinger satt motatt publisert til nå`() {
        val utbetalinger = utbetalingRepository.findUtbetalingDbRecordsByFnr(fnr)
        val naa = utbetalinger.first().motattPublisert.shouldNotBeNull()

        utbetalinger.forEach {
            it.motattPublisert.shouldBeEqualTo(naa)
        }
    }
}
