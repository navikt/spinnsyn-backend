package no.nav.helse.flex.jobb

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.helse.flex.db.*
import no.nav.helse.flex.domene.RSUtbetalingdag
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.domene.UtbetalingUtbetalt
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.BrukerVedtak.Companion.mapTilRsVedtakWrapper
import no.nav.helse.flex.util.leggTilDagerIVedtakPeriode
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Component
class MigrerTilUtbetalingsdagerJobb(
    private val utbetalingRepository: UtbetalingRepository,
    private val vedtakRepository: VedtakRepository,
    private val batchMigrator: MigrerTilUtbetalingsdagerBatchMigrator,
) {
    val log = logger()

    @Scheduled(initialDelay = 3_000, fixedDelay = 100, timeUnit = TimeUnit.MILLISECONDS)
    fun kjørMigreringTilUtbetalingsdager() {
        log.info("Migrerer gamle vedtak til nytt utbetalingsdager format")

        val utbetalinger = utbetalingRepository.hent500MedGammeltFormat()
        if (utbetalinger.isEmpty()) {
            log.info("Ingen flere vedtak med gammelt format å migrere")
            return
        }
        val utbetalingVedtakMap = utbetalinger.associateWith { vedtakRepository.findByUtbetalingId(it.utbetalingId) }

        batchMigrator
            .migrerGammeltVedtakAsync(utbetalingVedtakMap)
            .whenComplete { resultat, throwable ->
                if (throwable != null) {
                    log.error(
                        "Feilet ved migrering av batch med ${utbetalinger.size} utbetalinger til nytt utbetalingsdager format",
                        throwable,
                    )
                } else {
                    log.info(
                        "Migrert batch med ${utbetalinger.size} utbetalinger til nytt utbetalingsdager format: " +
                            resultat.toLogString(),
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
    @Async("fixedThreadPool")
    @Transactional(rollbackFor = [Exception::class])
    fun migrerGammeltVedtakAsync(
        utbetalingVedtakMap: Map<UtbetalingDbRecord, List<VedtakDbRecord>>,
    ): CompletableFuture<VedtakMigreringStatus> {
        val migrerteUtbetalinger =
            utbetalingVedtakMap.map { (utbetaling, vedtak) ->
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
            }
        utbetalingRepository.saveAll(migrerteUtbetalinger)
        return CompletableFuture.completedFuture(
            VedtakMigreringStatus(
                migrert = migrerteUtbetalinger.size,
            ),
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
