package no.nav.helse.flex.jobb

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.db.*
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
    private val environmentToggles: EnvironmentToggles,
) {
    val log = logger()

    @Scheduled(initialDelay = 180, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    @Transactional(rollbackFor = [Exception::class])
    fun kjørMigreringTilUtbetalingsdager() {
        if (!leaderElection.isLeader()) {
            return
        }

        log.info("Migrerer gamle vedtak til nytt utbetalingsdager format")

        val utbetalingerIder =
            if (environmentToggles.isProduction()) {
                utbetalingMigreringRepository.hentUtdragForDryRun().map { it.utbetalingId }
            } else {
                utbetalingMigreringRepository.findFirst500ByStatus(MigrertStatus.IKKE_MIGRERT).map { it.utbetalingId }
            }

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
        val utbetalingIder = utbetalingVedtakMap.keys.map { it.utbetalingId }
        val migreringsRecords = utbetalingMigreringRepository.findByUtbetalingIdIn(utbetalingIder).associateBy { it.utbetalingId }
        utbetalingMigreringRepository
            .findByUtbetalingIdIn(
                utbetalingIder,
            ).associateBy(UtbetalingMigreringDbRecord::utbetalingId)

        val migreringsResultat =
            utbetalingVedtakMap.map { (utbetaling, vedtak) ->
                try {
                    val migrertUtbetaling = migrerEnkelUtbetaling(utbetaling, vedtak)
                    MigreringsResultat.Suksess(migrertUtbetaling, migreringsRecords[utbetaling.utbetalingId]!!)
                } catch (e: Exception) {
                    log.error("Feilet migrering for utbetalingId=${utbetaling.utbetalingId}", e)
                    MigreringsResultat.Feil(migreringsRecords[utbetaling.utbetalingId]!!, e)
                }
            }

        val suksesser = migreringsResultat.filterIsInstance<MigreringsResultat.Suksess>()
        val feil = migreringsResultat.filterIsInstance<MigreringsResultat.Feil>()

        if (suksesser.isNotEmpty()) {
            lagreVellykkedeMigreringer(suksesser)
        }

        if (feil.isNotEmpty()) {
            lagreFeiledeMigreringer(feil)
        }

        if (environmentToggles.isProduction()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
            log.info("DB-dry-run: kjørte saveAll for ${suksesser.size} utbetalinger og rullet tilbake transaksjonen")
        }

        return VedtakMigreringStatus(
            migrert = suksesser.size,
            feilet = feil.size,
        )
    }

    private fun migrerEnkelUtbetaling(
        utbetaling: UtbetalingDbRecord,
        vedtak: List<VedtakDbRecord>,
    ): UtbetalingDbRecord {
        val annuleringer = annulleringDAO.finnAnnulleringMedIdent(listOf(utbetaling.fnr))
        val vedtakMedUtbetaling = vedtak.filter { it.utbetalingId == utbetaling.utbetalingId }
        val rsVedtak =
            mapTilRsVedtakWrapper(
                utbetalingDbRecord = utbetaling,
                vedtakMedUtbetaling = vedtakMedUtbetaling,
                annulleringer = annuleringer,
            ).leggTilDagerIVedtakPeriode()

        val utbetalingUtbetalt = objectMapper.readValue(utbetaling.utbetaling, UtbetalingUtbetalt::class.java)

        val dagerPersonMap = rsVedtak.dagerPerson.associateBy { it.dato }
        val dagerArbeidsgiverMap = rsVedtak.dagerArbeidsgiver.associateBy { it.dato }

        val utbetalingdagDtos =
            utbetalingUtbetalt.utbetalingsdager.map { gammelDag ->
                val dagPerson = dagerPersonMap[gammelDag.dato]
                val dagArbeidsgiver = dagerArbeidsgiverMap[gammelDag.dato]

                gammelDag.copy(
                    beløpTilSykmeldt = dagPerson?.belop ?: 0,
                    beløpTilArbeidsgiver = dagArbeidsgiver?.belop ?: 0,
                    sykdomsgrad = (dagPerson?.grad ?: dagArbeidsgiver?.grad ?: 0.0).toInt(),
                )
            }

        return utbetaling.copy(
            utbetaling = objectMapper.writeValueAsString(utbetalingUtbetalt.copy(utbetalingsdager = utbetalingdagDtos)),
        )
    }

    private fun lagreVellykkedeMigreringer(suksesser: List<MigreringsResultat.Suksess>) {
        utbetalingRepository.saveAll(suksesser.map { it.utbetaling })
        val oppdaterteMigreringer = suksesser.map { it.migreringsRecord.copy(status = MigrertStatus.MIGRERT) }
        utbetalingMigreringRepository.saveAll(oppdaterteMigreringer)
    }

    private fun lagreFeiledeMigreringer(feil: List<MigreringsResultat.Feil>) {
        val oppdaterteMigreringer = feil.map { it.migreringsRecord.copy(status = MigrertStatus.FEILET) }
        utbetalingMigreringRepository.saveAll(oppdaterteMigreringer)
    }

    private sealed class MigreringsResultat {
        data class Suksess(
            val utbetaling: UtbetalingDbRecord,
            val migreringsRecord: UtbetalingMigreringDbRecord,
        ) : MigreringsResultat()

        data class Feil(
            val migreringsRecord: UtbetalingMigreringDbRecord,
            val exception: Exception,
        ) : MigreringsResultat()
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
