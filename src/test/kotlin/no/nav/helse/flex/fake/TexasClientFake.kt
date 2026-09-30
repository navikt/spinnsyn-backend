package no.nav.helse.flex.fake

import no.nav.helse.flex.client.texas.TexasClient
import no.nav.helse.flex.client.texas.TexasResponse

class TexasClientFake(
    private val flexGruppe: String,
) : TexasClient {
    override fun introspect(
        identityProvider: String,
        token: String,
    ): TexasResponse =
        when (token) {
            "gyldig-flex-token" -> {
                TexasResponse(
                    active = true,
                    NAVident = "Z999999",
                    scp = "defaultaccess vedtak.read",
                    groups = listOf(flexGruppe),
                )
            }

            "gyldig-token-uten-gruppe" -> {
                TexasResponse(
                    active = true,
                    NAVident = "Z999999",
                    scp = "defaultaccess vedtak.read",
                    groups = listOf("annen-gruppe"),
                )
            }

            "gyldig-token-uten-scope" -> {
                TexasResponse(
                    active = true,
                    NAVident = "Z999999",
                    scp = "defaultaccess",
                    groups = listOf(flexGruppe),
                )
            }

            "gyldig-token-uten-scp-claim" -> {
                TexasResponse(
                    active = true,
                    NAVident = "Z999999",
                    groups = listOf(flexGruppe),
                )
            }

            "gyldig-token-uten-navident" -> {
                TexasResponse(
                    active = true,
                    scp = "vedtak.read",
                    groups = listOf(flexGruppe),
                )
            }

            "gyldig-token-med-lignende-scope" -> {
                TexasResponse(
                    active = true,
                    NAVident = "Z999999",
                    scp = "defaultaccess vedtak.read-extra",
                    groups = listOf(flexGruppe),
                )
            }

            else -> {
                TexasResponse(active = false, error = "ugyldig token")
            }
        }
}
