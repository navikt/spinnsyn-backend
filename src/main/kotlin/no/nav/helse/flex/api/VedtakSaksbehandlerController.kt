package no.nav.helse.flex.api

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat.*
import no.nav.helse.flex.client.texas.IngenTilgangException
import no.nav.helse.flex.client.texas.UautorisertException
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.db.UtbetalingRepository
import no.nav.helse.flex.domene.RSVedtakWrapper
import no.nav.helse.flex.kafka.producer.AuditEntry
import no.nav.helse.flex.kafka.producer.AuditLogProducer
import no.nav.helse.flex.kafka.producer.EventType
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.BrukerVedtak
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.net.URI
import java.util.UUID

const val SAKSBEHANDLER_API_PATH = "/api/v1/saksbehandler"

@Controller
@RequestMapping(SAKSBEHANDLER_API_PATH)
class VedtakSaksbehandlerController(
    private val clientIdValidation: ClientIdValidation,
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    private val utbetalingRepository: UtbetalingRepository,
    private val vedtakService: BrukerVedtak,
    private val tilgangskontroll: PopulasjonstilgangskontrollProvider,
    private val auditLogProducer: AuditLogProducer,
    @Value("\${SPEIL_BRUKERE_GROUP_ID}") speilBrukereGruppe: String,
    @Value("\${SPEIL_LESETILGANG_GROUP_ID}") speilLesetilgangGruppe: String,
) {
    private val log = logger()

    private val speilGrupper = setOf(speilBrukereGruppe, speilLesetilgangGruppe)

    @GetMapping("/vedtak/{utbetalingId}", produces = [APPLICATION_JSON_VALUE])
    @ResponseBody
    @ProtectedWithClaims(issuer = "azureator")
    fun hentVedtak(
        @PathVariable utbetalingId: String,
    ): ResponseEntity<HentVedtakForSaksbehandlerResponse> {
        clientIdValidation.validateClientId(
            NamespaceAndApp(namespace = "flex", app = "spinnsyn-frontend-saksbehandler"),
        )
        val token = azureToken()
        val navIdent = validerSaksbehandler(token.jwtTokenClaims)
        validerUtbetalingId(utbetalingId)

        val fnr = finnFnrForUtbetaling(utbetalingId)
        sjekkTilgangOgAuditlogg(accessToken = token.encodedToken, navIdent = navIdent, fnr = fnr, utbetalingId = utbetalingId)

        val vedtak = hentAlleVedtakTilPerson(fnr = fnr, utbetalingId = utbetalingId)
        log.info("Saksbehandler hentet vedtak for utbetalingId=$utbetalingId")
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(HentVedtakForSaksbehandlerResponse(vedtak = vedtak))
    }

    private fun validerSaksbehandler(claims: JwtTokenClaims): String {
        if (!claims.erMedlemAvEnAv(speilGrupper)) {
            throw IngenTilgangException("Saksbehandler er ikke medlem av speil-brukere eller Speil lesetilgang")
        }
        return claims.getStringClaim("NAVident") ?: throw UautorisertException("Fant ikke NAVident i token")
    }

    private fun validerUtbetalingId(utbetalingId: String) {
        val gyldig = runCatching { UUID.fromString(utbetalingId).toString() == utbetalingId }.getOrDefault(false)
        if (!gyldig) {
            throw UgyldigRequestException("utbetalingId må være gyldig UUID")
        }
    }

    private fun finnFnrForUtbetaling(utbetalingId: String): String =
        utbetalingRepository.findByUtbetalingId(utbetalingId)?.fnr
            ?: throw BrukerVedtak.VedtakIkkeFunnetException(utbetalingId)

    private fun sjekkTilgangOgAuditlogg(
        accessToken: String,
        navIdent: String,
        fnr: String,
        utbetalingId: String,
    ) {
        val resultat: TilgangskontrollResultat =
            try {
                tilgangskontroll.kontrollerKjerneTilgang(accessToken = accessToken, fødselsnummer = fnr)
            } catch (e: Exception) {
                throw TilgangskontrollUtilgjengeligException("Kall til Tilgangsmaskinen feilet", e)
            }

        when (resultat) {
            is Ok -> auditlogg(navIdent = navIdent, fnr = fnr, utbetalingId = utbetalingId, tillatt = true)
            is ManglerTilgang -> avvis(navIdent = navIdent, fnr = fnr, utbetalingId = utbetalingId, årsak = resultat.tilgangSomMangler.name)
            is IdentIkkeFunnet -> avvis(navIdent = navIdent, fnr = fnr, utbetalingId = utbetalingId, årsak = "IdentIkkeFunnet")
            is UventetFeil -> throw TilgangskontrollUtilgjengeligException(resultat.menneskeligLesbarForklaring)
        }
    }

    private fun avvis(
        navIdent: String,
        fnr: String,
        utbetalingId: String,
        årsak: String,
    ): Nothing {
        try {
            auditlogg(navIdent = navIdent, fnr = fnr, utbetalingId = utbetalingId, tillatt = false)
        } catch (e: Exception) {
            log.error(
                "Klarte ikke auditlogge avvist oppslag for saksbehandler (${e.javaClass.simpleName}), returnerer avslag likevel",
            )
        }
        throw IkkeTilgangSaksbehandlerException(årsak)
    }

    private fun hentAlleVedtakTilPerson(
        fnr: String,
        utbetalingId: String,
    ): List<RSVedtakWrapper> {
        val alleVedtak =
            vedtakService
                .hentVedtak(fnr, hentSomBruker = true)
                .map { it.copy(id = it.vedtak.utbetaling.utbetalingId ?: it.id) }
        if (alleVedtak.none { it.id == utbetalingId }) {
            throw BrukerVedtak.VedtakIkkeFunnetException(utbetalingId)
        }
        return alleVedtak
    }

    private fun auditlogg(
        navIdent: String,
        fnr: String,
        utbetalingId: String,
        tillatt: Boolean,
    ) {
        auditLogProducer.publiser(
            AuditEntry(
                appNavn = "spinnsyn-backend",
                utførtAv = navIdent,
                oppslagPå = fnr,
                eventType = EventType.READ,
                forespørselTillatt = tillatt,
                beskrivelse = "Saksbehandler henter vedtak fra Speil",
                requestUrl = URI.create("$SAKSBEHANDLER_API_PATH/vedtak/$utbetalingId"),
                requestMethod = "GET",
            ),
        )
    }

    private fun azureToken(): JwtToken =
        tokenValidationContextHolder
            .getTokenValidationContext()
            .getJwtToken("azureator")
            ?: throw UautorisertException("Fant ikke azure-token")
}

private fun JwtTokenClaims.erMedlemAvEnAv(grupper: Set<String>): Boolean =
    (get("groups") as? List<*>)?.any { it is String && it in grupper } == true

data class HentVedtakForSaksbehandlerResponse(
    val vedtak: List<RSVedtakWrapper>,
)

class IkkeTilgangSaksbehandlerException(
    årsak: String,
) : AbstractApiError(
        message = "Saksbehandler har ikke tilgang til vedtak: $årsak",
        httpStatus = HttpStatus.FORBIDDEN,
        reason = "INGEN_TILGANG",
        loglevel = LogLevel.WARN,
    )

class TilgangskontrollUtilgjengeligException(
    message: String,
    grunn: Throwable? = null,
) : AbstractApiError(
        message = message,
        httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
        reason = "TILGANGSKONTROLL_UTILGJENGELIG",
        loglevel = LogLevel.ERROR,
        grunn = grunn,
    )
