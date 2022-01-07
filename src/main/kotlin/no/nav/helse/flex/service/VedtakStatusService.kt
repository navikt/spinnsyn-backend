package no.nav.helse.flex.service

import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.kafka.VedtakStatusKafkaProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class VedtakStatusService(
    private val utbetalingRepository: UtbetalingRepository,
    private val vedtakRepository: VedtakRepository,
    private val metrikk: Metrikk,
    private val vedtakStatusKafkaProducer: VedtakStatusKafkaProducer,
    private val vedtakService: VedtakService,
) {

    private val log = logger()

    private val erHelg = { dag: RSDag -> dag.dagtype == "NavHelgDag" }
    private val erAgPeriode = { dag: RSDag -> dag.dagtype == "ArbeidsgiverperiodeDag" }
    private val erArbeid = { dag: RSDag -> dag.dagtype == "Arbeidsdag" }

    private fun List<RSDag>.ingenAndreDager() = all { dag ->
        listOf(erHelg, erAgPeriode, erArbeid).any { predicate ->
            predicate(dag)
        }
    }

    fun prosesserUtbetalinger(): Int {
        val utbetalinger = utbetalingRepository.utbetalingerKlarTilVarsling()
        if (utbetalinger.isEmpty()) return 0

        val vedtakGruppert = vedtakRepository
            .hentVedtakMedUtbetalingId(utbetalinger.map { it.utbetalingId })
            .groupBy { it }
            .map { it.key to it.value.size }

        val utbetalingerMedAlleVedtak = utbetalinger.filter { utbetaling ->
            vedtakGruppert
                .find {
                    it.first == utbetaling.utbetalingId &&
                        it.second == utbetaling.antallVedtak
                } != null
        }

        var sendt = 0

        utbetalingerMedAlleVedtak.forEach { ut ->
            val id = ut.id
            val fnr = ut.fnr
            val utbetalingId = ut.utbetalingId

            vedtakService.hentVedtak(fnr)
                .first { it.id == id }
                .run {
                    val alleDager = dagerArbeidsgiver + dagerPerson

                    if (alleDager.all(erHelg)) {
                        log.info("Utbetaling $utbetalingId inneholder bare NavHelgDag")
                        skalIkkeVises(id, "NavHelgDag")
                        return@run
                    }

                    if (alleDager.all(erAgPeriode)) {
                        log.info("Utbetaling $utbetalingId inneholder bare ArbeidsgiverperiodeDag")
                        skalIkkeVises(id, "ArbeidsgiverperiodeDag")
                        return@run
                    }

                    if (alleDager.all(erArbeid)) {
                        log.info("Utbetaling $utbetalingId inneholder bare Arbeidsdag")
                        skalIkkeVises(id, "Arbeidsdag")
                        return@run
                    }

                    if (alleDager.ingenAndreDager()) {
                        log.info("Utbetaling $utbetalingId inneholder bare dager der NAV ikke er involvert")
                        skalIkkeVises(id, "IngenAndreDager")
                        return@run
                    }

                    vedtakStatusKafkaProducer.produserMelding(
                        VedtakStatusDTO(
                            id = id,
                            fnr = fnr,
                            vedtakStatus = VedtakStatus.MOTATT
                        )
                    )
                    utbetalingRepository.settSkalVisesOgMotattPublisert(
                        motattPublisert = Instant.now(),
                        skalVisesTilBruker = true,
                        id = id,
                    )

                    metrikk.STATUS_MOTATT.increment()
                    sendt += 1
                }
        }

        if (sendt != 0) log.info("Sendte motatt status for $sendt vedtak")
        return sendt
    }

    private fun skalIkkeVises(id: String, grunn: String) {
        utbetalingRepository.settSkalVisesOgMotattPublisert(
            skalVisesTilBruker = false,
            motattPublisert = null,
            id = id,
        )
        metrikk.skalIkkeVises(grunn).increment()
    }
}
