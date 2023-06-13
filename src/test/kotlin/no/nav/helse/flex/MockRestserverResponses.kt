package no.nav.helse.flex

import no.nav.helse.flex.client.syfotilgangskontroll.SyfoTilgangskontrollOboClient.Companion.NAV_PERSONIDENT_HEADER
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest

fun AbstractContainerBaseTest.mockSyfoTilgangskontroll(
    tilgang: Boolean,
    fnr: String
) {
    syfotilgangskontrollMockRestServiceServer!!
        .expect(requestTo("http://syfotilgangskontroll/syfo-tilgangskontroll/api/tilgang/navident/person"))
        .andExpect(header(NAV_PERSONIDENT_HEADER, fnr))
        .andRespond(
            if (tilgang) {
                withSuccess(
                    objectMapper.writeValueAsBytes(
                        "Har tilgang"
                    ),
                    MediaType.APPLICATION_JSON
                )
            } else {
                withUnauthorizedRequest()
            }
        )
}
