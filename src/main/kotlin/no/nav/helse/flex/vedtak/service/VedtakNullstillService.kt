package no.nav.helse.flex.vedtak.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.Environment
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProducer
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.log
import no.nav.helse.flex.vedtak.db.finnVedtak
import java.lang.IllegalStateException
import java.time.Instant

@KtorExperimentalAPI
class VedtakNullstillService(
    private val database: DatabaseInterface,
    private val brukernotifikasjonKafkaProducer: BrukernotifikasjonKafkaProducer,
    private val environment: Environment
) {

    fun nullstill(fnr: String): Int {
        if (environment.isProd()) {
            throw IllegalStateException("Kan ikke nullstille i produksjon")
        }
        val vedtak = database.finnVedtak(fnr)
        vedtak.forEach {
            if (!it.lest) {
                // Fjern brukernotifikasjonen
                brukernotifikasjonKafkaProducer.sendDonemelding(
                    Nokkel(environment.serviceuserUsername, it.id),
                    Done(Instant.now().toEpochMilli(), fnr, it.id)
                )
            }
            database.slettVedtak(vedtakId = it.id, fnr = fnr)
        }

        return vedtak.size
    }
}

private fun DatabaseInterface.slettVedtak(vedtakId: String, fnr: String) {
    connection.use { connection ->
        log.info("Sletter vedtak id $vedtakId")
        connection.prepareStatement(
            """
                DELETE FROM vedtak
                WHERE id = ?
                AND fnr = ?;
            """
        ).use {
            it.setString(1, vedtakId)
            it.setString(2, fnr)
            it.execute()
        }
        connection.commit()

        log.info("Utført: slettet vedtak id $vedtakId")
    }
}
