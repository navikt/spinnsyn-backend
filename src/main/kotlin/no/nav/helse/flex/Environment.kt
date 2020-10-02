package no.nav.helse.flex

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val spinnsynBackendDbHost: String = getEnvVar("NAIS_DATABASE_SPINNSYN_BACKEND_SPINNSYN_DB_HOST"),
    val spinnsynBackendDbPort: String = getEnvVar("NAIS_DATABASE_SPINNSYN_BACKEND_SPINNSYN_DB_PORT"),
    val spinnsynBackendDbName: String = getEnvVar("NAIS_DATABASE_SPINNSYN_BACKEND_SPINNSYN_DB_DATABASE"),
    val spinnsynBackendDbUsername: String = getEnvVar("NAIS_DATABASE_SPINNSYN_BACKEND_SPINNSYN_DB_USERNAME"),
    val spinnsynBackendDbPassword: String = getEnvVar("NAIS_DATABASE_SPINNSYN_BACKEND_SPINNSYN_DB_PASSWORD"),
    val spinnsynFrontendUrl: String = getEnvVar("SPINNSYN_FRONTEND_URL"),
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),
    val selvbetjeningWellKnownUri: String = getEnvVar("SELVBETJENING_WELLKNOWN_URI"),
    val selvbetjeningExpectedAudience: List<String> = getEnvVar("SELVBETJENING_EXPECTED_AUDIENCE").split(","),
    val veilederWellKnownUri: String = getEnvVar("VEILEDER_WELLKNOWN_URI"),
    val veilederExpectedAudience: List<String> = getEnvVar("VEILEDER_EXPECTED_AUDIENCE").split(","),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val sidecarInitialDelay: Long = getEnvVar("SIDECAR_INITIAL_DELAY", "30000").toLong()
) : KafkaConfig {

    fun hentKafkaCredentials(): KafkaCredentials {
        return object : KafkaCredentials {
            override val kafkaPassword: String
                get() = serviceuserPassword
            override val kafkaUsername: String
                get() = serviceuserUsername
        }
    }

    fun jdbcUrl(): String {
        return "jdbc:postgresql://$spinnsynBackendDbHost:$spinnsynBackendDbPort/$spinnsynBackendDbName"
    }

    fun isProd(): Boolean {
        return cluster == "prod-gcp"
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
