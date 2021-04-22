package no.nav.helse.flex

import no.nav.helse.flex.client.SyfoTilgangskontrollClient
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import java.net.URI

fun MockRestServiceServer.mockTilgangskontrollResponse(tilgang: Boolean, fnr: String, veilederToken: String) {
    this.expect(
        once(),
        requestTo(URI("http://syfotilgangskontroll/syfo-tilgangskontroll/api/tilgang/bruker?fnr=$fnr"))
    )
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer $veilederToken"))
        .andRespond(
            withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    SyfoTilgangskontrollClient.Tilgang(harTilgang = tilgang, begrunnelse = null).serialisertTilString()
                )
        )
}
