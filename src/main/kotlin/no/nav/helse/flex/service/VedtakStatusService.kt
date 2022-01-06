package no.nav.helse.flex.service

import no.nav.helse.flex.db.UtbetalingRepository
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
        val utbetalingerIderOgFnr = utbetalingRepository.utbetalingerKlarTilVarsling()
        var sendt = 0

        utbetalingerIderOgFnr.forEach { (dbId, fnr) ->
            vedtakService
                .hentVedtak(fnr)
                .first { it.id == dbId }
                .run {
                    val alleDager = dagerArbeidsgiver + dagerPerson

                    // TODO: Sett skalVisesTilBruker til false, og gå videre til neste utbetaling
                    if (alleDager.all(erHelg)) {
                        log.info("Utbetaling $dbId inneholder bare NavHelgDag")
                    }

                    if (alleDager.all(erAgPeriode)) {
                        log.info("Utbetaling $dbId inneholder bare ArbeidsgiverperiodeDag")
                    }

                    if (alleDager.all(erArbeid)) {
                        log.info("Utbetaling $dbId inneholder bare Arbeidsdag")
                    }

                    if (alleDager.ingenAndreDager()) {
                        log.info("Utbetaling $dbId inneholder bare dager der NAV ikke er innvolvert")
                    }

                    vedtakStatusKafkaProducer.produserMelding(
                        VedtakStatusDTO(
                            id = dbId,
                            fnr = fnr,
                            vedtakStatus = VedtakStatus.MOTATT
                        )
                    )
                    utbetalingRepository.settSkalVisesOgMotattPublisert(
                        motattPublisert = Instant.now(),
                        skalVisesTilBruker = null, // TODO: Sett til true når vi vet at denne skal vises
                        id = dbId,
                    )

                    metrikk.STATUS_MOTATT.increment()
                    sendt += 1
                }
        }

        if (sendt != 0) {
            log.info("Sendte motatt status for $sendt vedtak")
        }
        return sendt
    }
}
