package no.nav.helse.flex.jobb

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.helse.flex.db.*
import no.nav.helse.flex.domene.RSUtbetalingdag
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.BrukerVedtak.Companion.mapTilRsVedtakWrapper
import no.nav.helse.flex.util.leggTilDagerIVedtakPeriode
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Component
class MigrerTilUtbetalingsdagerJobb(
    private val utbetalingRepository: UtbetalingRepository,
    private val vedtakRepository: VedtakRepository,
    private val batchMigrator: MigrerTilUtbetalingsdagerBatchMigrator,
) {
    val log = logger()
    private var offset = 0

    fun resetOffset() {
        offset = 0
    }

    @Scheduled(initialDelay = 3_000, fixedDelay = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Transactional(rollbackFor = [Exception::class])
    fun kjørMigreringTilUtbetalingsdager() {
        log.info("Migrerer gamle vedtak til nytt utbetalingsdager format (offset=$offset)")

        val utbetalinger = utbetalingRepository.hent500MedGammeltFormatMedOffset(offset)
        if (utbetalinger.isEmpty()) {
            log.info("Ingen flere vedtak med gammelt format å migrere")
            return
        }
        val utbetalingVedtakMap = utbetalinger.associateWith { vedtakRepository.findByUtbetalingId(it.utbetalingId) }

        batchMigrator
            .migrerGammeltVedtak(utbetalingVedtakMap)
            .apply {
                if (feilet != null) {
                    log.error(
                        "Feilet ved migrering av batch med ${utbetalinger.size} utbetalinger til nytt utbetalingsdager format " +
                            "(offset=$offset)" + this.toLogString(),
                    )
                } else {
                    log.info(
                        "Migrert batch med ${utbetalinger.size} utbetalinger til nytt utbetalingsdager format (offset=$offset): " +
                            this.toLogString(),
                    )
                }
            }
    }
}

@Component
class MigrerTilUtbetalingsdagerBatchMigrator(
    private val utbetalingRepository: UtbetalingRepository,
    private val annulleringDAO: AnnulleringDAO,
    private val objectMapper: ObjectMapper,
) {
    private val log = logger()

    @Transactional(rollbackFor = [Exception::class])
    fun migrerGammeltVedtak(utbetalingVedtakMap: Map<UtbetalingDbRecord, List<VedtakDbRecord>>): VedtakMigreringStatus {
        var feilet = 0

        val migrerteUtbetalinger =
            utbetalingVedtakMap.mapNotNull { (utbetaling, vedtak) ->
                try {
                    val annuleringer = annulleringDAO.finnAnnulleringMedIdent(listOf(utbetaling.fnr))
                    val vedtakMedUtbetaling = vedtak.filter { it.utbetalingId == utbetaling.utbetalingId }
                    val rsVedtak =
                        mapTilRsVedtakWrapper(
                            utbetalingDbRecord = utbetaling,
                            vedtakMedUtbetaling = vedtakMedUtbetaling,
                            annulleringer = annuleringer,
                        ).leggTilDagerIVedtakPeriode()

                    val utbetalingsdager = RSVedtakWrapper.dagerTilUtbetalingsdager(rsVedtak.dagerPerson, rsVedtak.dagerArbeidsgiver)
                    val utbetalingdagDtos = utbetalingsdager.map { RSUtbetalingdag.konverterTilUtbetalindagDto(it) }
                    val utbetalingUtbetalt =
                        objectMapper.readValue(utbetaling.utbetaling, UtbetalingUtbetalt::class.java).copy(
                            utbetalingsdager = utbetalingdagDtos,
                        )

                    UtbetalingDbRecord(
                        id = utbetaling.id,
                        fnr = utbetaling.fnr,
                        utbetaling = objectMapper.writeValueAsString(utbetalingUtbetalt),
                        opprettet = utbetaling.opprettet,
                        utbetalingId = utbetaling.utbetalingId,
                        utbetalingType = utbetaling.utbetalingType,
                        antallVedtak = utbetaling.antallVedtak,
                        lest = utbetaling.lest,
                        motattPublisert = utbetaling.motattPublisert,
                        skalVisesTilBruker = utbetaling.skalVisesTilBruker,
                    )
                } catch (e: Exception) {
                    feilet++
                    log.warn("Feilet migrering for utbetalingId=${utbetaling.utbetalingId}", e)
                    null
                }
            }

        if (migrerteUtbetalinger.isNotEmpty()) {
            utbetalingRepository.saveAll(migrerteUtbetalinger)
        }

        return VedtakMigreringStatus(
            migrert = migrerteUtbetalinger.size,
            feilet = feilet,
        )
    }
}

class VedtakMigreringStatus(
    val migrert: Int? = null,
    val feilet: Int? = null,
) {
    fun toLogString(): String {
        val deler = mutableListOf<String>()
        migrert?.let { deler.add("Migrert: $it") }
        feilet?.let { deler.add("Feilet: $it") }
        return deler.joinToString(", ")
    }
}
