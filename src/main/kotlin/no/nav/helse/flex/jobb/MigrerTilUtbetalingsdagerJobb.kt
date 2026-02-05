package no.nav.helse.flex.jobb

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.cronjob.LeaderElection
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

@Component
class MigrerTilUtbetalingsdagerJobb(
    private val utbetalingRepository: UtbetalingRepository,
    private val vedtakRepository: VedtakRepository,
    private val batchMigrator: MigrerTilUtbetalingsdagerBatchMigrator,
    private val leaderElection: LeaderElection,
    private val utbetalingMigreringRepository: UtbetalingMigreringRepository,
) {
    val log = logger()

    @Scheduled(initialDelay = 3_000, fixedDelay = 1_000, timeUnit = TimeUnit.MILLISECONDS)
    @Transactional(rollbackFor = [Exception::class])
    fun kjørMigreringTilUtbetalingsdager() {
        if (!leaderElection.isLeader()) {
            return
        }

        log.info("Migrerer gamle vedtak til nytt utbetalingsdager format")

        val utbetalingerIder = utbetalingMigreringRepository.findFirst500ByStatus(MigrertStatus.IKKE_MIGRERT).map { it.utbetalingId }

        if (utbetalingerIder.isEmpty()) {
            log.info("Ingen flere vedtak med gammelt format å migrere")
            return
        }

        val utbetalinger = utbetalingRepository.findByUtbetalingIdIn(utbetalingerIder)
        val vedtak = vedtakRepository.findByUtbetalingIdIn(utbetalinger.map { it.utbetalingId })
        val utbetalingVedtakMap = utbetalinger.associateWith { utbetaling -> vedtak.filter { it.utbetalingId == utbetaling.utbetalingId } }

        val status = batchMigrator.migrerGammeltVedtak(utbetalingVedtakMap)

        if (status.feilet > 0) {
            log.error(
                "Noen migreringer av utbetalinger feilet: ${status.toLogString()}",
            )
        } else {
            log.info(
                "Migrert batch med ${utbetalinger.size} utbetalinger til nytt utbetalingsdager format: ${status.toLogString()}",
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
    private val utbetalingMigreringRepository: UtbetalingMigreringRepository,
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
                    val migrering = utbetalingMigreringRepository.findByUtbetalingIdIn(listOf(utbetaling.utbetalingId)).single()
                    migrering.copy(
                        status = MigrertStatus.FEILET,
                    )
                    utbetalingMigreringRepository.save(migrering)
                    log.error("Feilet migrering for utbetalingId=${utbetaling.utbetalingId}", e)
                    null
                }
            }

        if (migrerteUtbetalinger.isNotEmpty()) {
            utbetalingRepository.saveAll(migrerteUtbetalinger)
            val alleMigreringer =
                migrerteUtbetalinger.map {
                    val migrering = utbetalingMigreringRepository.findByUtbetalingIdIn(listOf(it.utbetalingId)).single()
                    migrering.copy(
                        status = MigrertStatus.MIGRERT,
                    )
                }
            utbetalingMigreringRepository.saveAll(alleMigreringer)
            if (environmentToggles.isProduction()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
                log.info("DB-dry-run: kjørte saveAll for ${migrerteUtbetalinger.size} utbetalinger og rullet tilbake transaksjonen")
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
