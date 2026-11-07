package no.nav.helse.flex.client.tilgangsmaskin

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.client.TilgangsmaskinenClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!test")
class TilgangsmaskinConfig {
    @Bean
    fun populasjonstilgangskontrollProvider(): PopulasjonstilgangskontrollProvider = TilgangsmaskinenClient.fromEnv()
}
