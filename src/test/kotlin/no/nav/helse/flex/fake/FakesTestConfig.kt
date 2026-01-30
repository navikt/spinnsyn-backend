package no.nav.helse.flex.fake

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class FakesTestConfig {
    @Bean
    fun environmentToggles(): EnvironmentTogglesFake = EnvironmentTogglesFake()
}
