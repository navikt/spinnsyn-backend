package no.nav.helse.flex.vedtak.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.flex.Environment
import no.nav.helse.flex.brukernotifkasjon.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.db.DatabaseInterface
import no.nav.helse.flex.vedtak.db.finnVedtak
import no.nav.helse.flex.vedtak.db.slettAnnulleringer
import no.nav.helse.flex.vedtak.db.slettVedtak
import java.lang.IllegalStateException
import java.time.Instant

@KtorExperimentalAPI
class VedtakNullstillService(
    private val database: DatabaseInterface,
    private val brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent,
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
                brukernotifikasjonKafkaProdusent.sendDonemelding(
                    Nokkel(environment.serviceuserUsername, it.id),
                    Done(Instant.now().toEpochMilli(), fnr, it.id)
                )
            }
            database.slettVedtak(vedtakId = it.id, fnr = fnr)
        }
        database.slettAnnulleringer(fnr)
        return vedtak.size
    }
}
