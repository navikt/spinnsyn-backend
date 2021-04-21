package no.nav.helse.flex

class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val syfotilgangskontrollUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
    val loginserviceIdportenDiscoveryUrl: String = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
    val loginserviceIdportenAudience: List<String> = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(","),
    val veilederWellKnownUri: String = getEnvVar("VEILEDER_WELLKNOWN_URI"),
    val veilederExpectedAudience: List<String> = getEnvVar("VEILEDER_EXPECTED_AUDIENCE").split(","),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val sidecarInitialDelay: Long = getEnvVar("SIDECAR_INITIAL_DELAY", "30000").toLong(),
    val kafkaAutoOffsetReset: String = getEnvVar("KAFKA_AUTO_OFFSET_RESET", "none"),
) {
    fun isProd(): Boolean {
        return cluster == "prod-gcp"
    }

    fun isDev(): Boolean {
        return cluster == "dev-gcp"
    }
}

private fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
