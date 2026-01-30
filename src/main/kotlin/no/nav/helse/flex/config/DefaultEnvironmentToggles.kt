package no.nav.helse.flex.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DefaultEnvironmentToggles(
    @param:Value($$"${NAIS_CLUSTER_NAME}") private val naisCluster: String,
) : EnvironmentToggles {
    override fun isProduction() = "prod-gcp" == naisCluster

    override fun isDevelopment() = !isProduction()
}

interface EnvironmentToggles {
    fun isProduction(): Boolean

    fun isDevelopment(): Boolean
}
