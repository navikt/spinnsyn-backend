package no.nav.helse.flex.service

import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.db.VedtakRepository
import no.nav.helse.flex.domene.RSDag
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.kafka.VedtakStatusKafkaProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.vedtaktype.Vedtaktype
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SendVedtakStatus(
    private val utbetalingRepository: UtbetalingRepository,
    private val vedtakRepository: VedtakRepository,
    private val vedtakStatusKafkaProducer: VedtakStatusKafkaProducer,
    private val vedtakService: BrukerVedtak,
    private val vedtaktype: Vedtaktype,
) {
    private val log = logger()

    companion object {
        private val erAgPeriode = { dag: RSDag -> dag.dagtype == "ArbeidsgiverperiodeDag" }
        private val erArbeid = { dag: RSDag -> dag.dagtype == "Arbeidsdag" }

        private fun List<RSDag>.erAgPeriodeMedArbeid() =
            all { dag ->
                listOf(erAgPeriode, erArbeid).any { predicate ->
                    predicate(dag)
                }
            }.and(erAgPeriode(last()))

        fun sjekkDager(dager: List<RSDag>): String {
            if (dager.all(erAgPeriode)) {
                return "ArbeidsgiverperiodeDag"
            }

            if (dager.erAgPeriodeMedArbeid()) {
                return "ArbeidsgiverperiodeMedArbeid"
            }

            return ""
        }
    }

    fun prosesserUtbetalinger(): Int {
        val utbetalinger = utbetalingRepository.utbetalingerKlarTilVarsling()
        if (utbetalinger.isEmpty()) return 0

        val vedtakGruppert =
            vedtakRepository
                .hentVedtakMedUtbetalingId(utbetalinger.map { it.utbetalingId })
                .groupBy { it }
                .map { it.key to it.value.size }

        val utbetalingerMedAlleVedtak =
            utbetalinger.filter { utbetaling ->
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
            val vedtakWrapper =
                vedtakService.hentVedtak(
                    fnr = fnr,
                    hentSomBruker = false,
                ).first { it.id == id }

            val skalIkkeVisesFordi = sjekkDager(vedtakWrapper.dagerArbeidsgiver + vedtakWrapper.dagerPerson)
            if (skalIkkeVisesFordi.isNotBlank()) {
                log.info("Utbetaling $utbetalingId inneholder bare $skalIkkeVisesFordi og vises ikke til bruker")
                skalIkkeVises(id, skalIkkeVisesFordi)
                return@forEach
            }

            vedtakStatusKafkaProducer.produserMelding(
                VedtakStatusDTO(
                    id = id,
                    fnr = fnr,
                    vedtakStatus = VedtakStatus.MOTATT,
                ),
            )
            utbetalingRepository.settSkalVisesOgMotattPublisert(
                motattPublisert = Instant.now(),
                skalVisesTilBruker = true,
                id = id,
            )
            sendt += 1
        }

        return sendt
    }

    private fun skalIkkeVises(
        id: String,
        grunn: String,
    ) {
        utbetalingRepository.settSkalVisesOgMotattPublisert(
            skalVisesTilBruker = false,
            motattPublisert = null,
            id = id,
        )
    }
}
