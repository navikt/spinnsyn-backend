package no.nav.helse.flex.client.texas

import no.nav.helse.flex.fake.TexasClientFake
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

private const val FLEX_GRUPPE = "flex-gruppe"

class TokenValideringServiceTest {
    private val service = TokenValideringService(TexasClientFake(FLEX_GRUPPE), FLEX_GRUPPE)

    @Test
    fun `returnerer ansatt for aktivt entra-token`() {
        val navIdent = service.validerTilgangOgHentNavIdent("gyldig-flex-token", "entra_id")

        navIdent shouldBeEqualTo "Z999999"
    }

    @Test
    fun `avviser manglende token`() {
        assertThrows(UautorisertException::class.java) {
            service.validerTilgangOgHentNavIdent(null, "entra_id")
        }
    }

    @Test
    fun `avviser inaktivt token`() {
        assertThrows(UautorisertException::class.java) {
            service.validerTilgangOgHentNavIdent("ikke-gyldig-token", "entra_id")
        }
    }

    @Test
    fun `avviser token uten navident`() {
        assertThrows(UautorisertException::class.java) {
            service.validerTilgangOgHentNavIdent("gyldig-token-uten-navident", "entra_id")
        }
    }

    @Test
    fun `avviser ansatt utenfor flex-gruppen selv med vedtaksscope`() {
        assertThrows(IngenTilgangException::class.java) {
            service.validerTilgangOgHentNavIdent("gyldig-token-uten-gruppe", "entra_id")
        }
    }

    @Test
    fun `avviser aktivt token uten vedtaksscope`() {
        assertThrows(IngenTilgangException::class.java) {
            service.validerTilgangOgHentNavIdent("gyldig-token-uten-scope", "entra_id")
        }
    }

    @Test
    fun `avviser token uten scp-claim`() {
        assertThrows(IngenTilgangException::class.java) {
            service.validerTilgangOgHentNavIdent("gyldig-token-uten-scp-claim", "entra_id")
        }
    }

    @Test
    fun `avviser delvis treff i scope`() {
        assertThrows(IngenTilgangException::class.java) {
            service.validerTilgangOgHentNavIdent("gyldig-token-med-lignende-scope", "entra_id")
        }
    }
}
