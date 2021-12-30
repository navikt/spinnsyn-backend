package no.nav.helse.flex.arkivering

import no.nav.helse.flex.kafka.ArkiveringDTO
import no.nav.helse.flex.kafka.VedtakArkiveringKafkaProducer
import no.nav.helse.flex.logger
import org.springframework.stereotype.Service

@Service
class VedtakArkiveringService(
    private val arkiveringRepository: VedtakArkiveringRepository,
    private val kafkaProducer: VedtakArkiveringKafkaProducer,
) {

    val log = logger()

    fun arkiverUtbetalinger(batchSize: Int) {
        val utbetalinger = arkiveringRepository.hentUtbetalinger(batchSize)
        utbetalinger.forEach { kafkaProducer.produserMelding(it.tilArkiveringDto()) }

        log.info("Sendt ${utbetalinger.size} utbetalinger til arkivering.")
        arkiveringRepository.settUtbetalingerTilArkivert(utbetalinger.map { it.id }.toList())
    }

    fun arkiverRetroVedtak(batchSize: Int) {
        val vedtak = arkiveringRepository.hentRetroVedtak(batchSize)
        vedtak.forEach { kafkaProducer.produserMelding(it.tilArkiveringDto()) }

        log.info("Sendt ${vedtak.size} retro vedtak til arkivering.")
        arkiveringRepository.settRetroVedtakTilArkivert(vedtak.map { it.id }.toList())
    }

    private fun VedtakArkiveringDTO.tilArkiveringDto(): ArkiveringDTO {
        return ArkiveringDTO(id = this.id, fnr = this.fnr)
    }
}
