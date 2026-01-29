package no.nav.helse.flex.jobb

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.helse.flex.config.EnvironmentToggles
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
import org.springframework.transaction.interceptor.TransactionAspectSupport
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Component
class MigrerTilUtbetalingsdagerJobb(
    private val utbetalingRepository: UtbetalingRepository,
    private val vedtakRepository: VedtakRepository,
    private val batchMigrator: MigrerTilUtbetalingsdagerBatchMigrator,
) {
    val log = logger()
    var offset = AtomicInteger(0)

    @Scheduled(initialDelay = 3_000, fixedDelay = 100, timeUnit = TimeUnit.MILLISECONDS)
    @Transactional(rollbackFor = [Exception::class])
    fun kjørMigreringTilUtbetalingsdager() {
        val gjeldendeOffset = offset.get()
        log.info("Migrerer gamle vedtak til nytt utbetalingsdager format (offset=$gjeldendeOffset)")

        val utbetalinger = utbetalingRepository.hent500MedGammeltFormatMedOffset(gjeldendeOffset)
        if (utbetalinger.isEmpty()) {
            log.info("Ingen flere vedtak med gammelt format å migrere")
            return
        }

        val vedtak = vedtakRepository.findByUtbetalingIdIn(utbetalinger.map { it.utbetalingId })
        val utbetalingVedtakMap = utbetalinger.associateWith { utbetaling -> vedtak.filter { it.utbetalingId == utbetaling.utbetalingId } }

        val status = batchMigrator.migrerGammeltVedtak(utbetalingVedtakMap)

        if (status.feilet > 0) {
            offset.getAndUpdate { it + status.feilet }
            log.error(
                "Feilet ved migrering av batch med ${utbetalinger.size} utbetalinger til nytt utbetalingsdager format " +
                    "(offset=$gjeldendeOffset): ${status.toLogString()}",
            )
        } else {
            log.info(
                "Migrert batch med ${utbetalinger.size} utbetalinger til nytt utbetalingsdager format " +
                    "(offset=$gjeldendeOffset): ${status.toLogString()}",
            )
        }
    }
}

@Component
class MigrerTilUtbetalingsdagerBatchMigrator(
    private val utbetalingRepository: UtbetalingRepository,
    private val annulleringDAO: AnnulleringDAO,
    private val objectMapper: ObjectMapper,
    private val environmentToggles: EnvironmentToggles,
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

                    utbetaling.copy(
                        utbetaling = objectMapper.writeValueAsString(utbetalingUtbetalt),
                    )
                } catch (e: Exception) {
                    feilet++
                    log.warn("Feilet migrering for utbetalingId=${utbetaling.utbetalingId}", e)
                    null
                }
            }

        if (migrerteUtbetalinger.isNotEmpty()) {
            if (environmentToggles.isProduction()) {
                utbetalingRepository.saveAll(migrerteUtbetalinger)
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
                log.info("DB-dry-run: kjørte saveAll for ${migrerteUtbetalinger.size} utbetalinger og rullet tilbake transaksjonen")
            } else {
                utbetalingRepository.saveAll(migrerteUtbetalinger)
            }
        }

        return VedtakMigreringStatus(
            migrert = migrerteUtbetalinger.size,
            feilet = feilet,
        )
    }
}

class VedtakMigreringStatus(
    val migrert: Int = 0,
    val feilet: Int = 0,
) {
    fun toLogString(): String {
        val deler = mutableListOf<String>()
        deler.add("Migrert: $migrert")
        deler.add("Feilet: $feilet")
        return deler.joinToString(", ")
    }
}
