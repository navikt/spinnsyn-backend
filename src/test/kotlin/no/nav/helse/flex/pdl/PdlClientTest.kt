package no.nav.helse.flex.pdl

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.pdl.PdlClient
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired

class PdlClientTest : FellesTestOppsett() {
    @Autowired
    private lateinit var pdlClient: PdlClient

    @Test
    fun `PdlClient er AOP-proxyet slik at @Retryable faktisk har effekt`() {
        // Verifiserer at PdlClient faktisk er proxyet sånn at @Retryable har effekt.
        AopUtils.isAopProxy(pdlClient).shouldBeTrue()
    }

    @Test
    fun `Vi tester happycase`() {
        val responseData = pdlClient.hentIdenterMedHistorikk("31111111111")

        responseData.map { it.ident } shouldBe listOf("31111111111", "11111111111", "21111111111", "3111111111100")

        val takeRequest = pdlMockWebserver.takeRequest()
        takeRequest.headers["TEMA"] shouldBe "SYK"
        takeRequest.method shouldBe "POST"
        takeRequest.headers["Authorization"]!!.shouldStartWith("Bearer ey")
    }
}
