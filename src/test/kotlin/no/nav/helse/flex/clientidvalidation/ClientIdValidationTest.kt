package no.nav.helse.flex.clientidvalidation

import no.nav.helse.flex.FellesTestOppsett
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ClientIdValidationTest : FellesTestOppsett() {
    @Autowired
    lateinit var clientIdValidation: ClientIdValidation

    @Test
    fun `godkjenner aktivt token med vedtaksscope og NAVident`() {
        val response =
            mockMvc
                .perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/flex/vedtak/soknad")
                        .header("Authorization", "Bearer gyldig-flex-token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fnr\":\"12345678901\",\"soknadId\":\"11111111-1111-1111-1111-111111111111\"}"),
                ).andReturn()
                .response

        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `returnerer 401 ved manglende NAVident`() {
        val response =
            mockMvc
                .perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/flex/vedtak/soknad")
                        .header("Authorization", "Bearer gyldig-token-uten-navident")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fnr\":\"12345678901\",\"soknadId\":\"11111111-1111-1111-1111-111111111111\"}"),
                ).andReturn()
                .response

        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `avviser token uten vedtaksscope`() {
        val response =
            mockMvc
                .perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/flex/vedtak/soknad")
                        .header("Authorization", "Bearer gyldig-token-uten-scope")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fnr\":\"12345678901\",\"soknadId\":\"11111111-1111-1111-1111-111111111111\"}"),
                ).andReturn()
                .response

        assertThat(response.status).isEqualTo(403)
    }

    @Test
    fun `avviser ansatt utenfor flex-gruppen`() {
        val response =
            mockMvc
                .perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/flex/vedtak/soknad")
                        .header("Authorization", "Bearer gyldig-token-uten-gruppe")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fnr\":\"12345678901\",\"soknadId\":\"11111111-1111-1111-1111-111111111111\"}"),
                ).andReturn()
                .response

        assertThat(response.status).isEqualTo(403)
    }
}
