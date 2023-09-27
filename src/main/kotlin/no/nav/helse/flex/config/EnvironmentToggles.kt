package no.nav.helse.flex.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EnvironmentToggles(
    @Value("\${nais.cluster}") private val naisCluster: String
) {
    fun isProduction() = "prod-gcp" == naisCluster
    fun isDevGcp() = "dev-gcp" == naisCluster
}
