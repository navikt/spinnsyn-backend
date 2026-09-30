package no.nav.helse.flex.fake

import no.nav.helse.flex.client.texas.TexasClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class FakesTestConfig {
    @Bean
    fun environmentToggles(): EnvironmentTogglesFake = EnvironmentTogglesFake()

    @Bean
    fun texasClient(
        @Value("\${FLEX_GROUP_ID}") flexGruppe: String,
    ): TexasClient = TexasClientFake(flexGruppe)
}
