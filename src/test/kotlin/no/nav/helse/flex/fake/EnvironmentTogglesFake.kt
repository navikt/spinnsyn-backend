package no.nav.helse.flex.fake

import no.nav.helse.flex.config.EnvironmentToggles

class EnvironmentTogglesFake : EnvironmentToggles {
    companion object {
        private const val DEFAULT_ENVIRONMENT = "prod"
    }

    private var environment = DEFAULT_ENVIRONMENT

    override fun isProduction(): Boolean = environment == "prod"

    override fun isDevelopment(): Boolean = environment == "dev"

    fun setEnvironment(environment: String) {
        this.environment = environment
    }

    fun reset() {
        this.environment = DEFAULT_ENVIRONMENT
    }
}
