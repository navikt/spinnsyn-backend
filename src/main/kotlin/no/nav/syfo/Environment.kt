package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val spinnsynBackendDbHost: String = getEnvVar("NAIS_DATABASE_SPINNSYNBACKEND_SPINNSYNDB_HOST"),
    val spinnsynBackendDbPort: String = getEnvVar("NAIS_DATABASE_SPINNSYNBACKEND_SPINNSYNDB_PORT"),
    val spinnsynBackendDbName: String = getEnvVar("NAIS_DATABASE_SPINNSYNBACKEND_SPINNSYNDB_DATABASE"),
    val spinnsynBackendDbUsername: String = getEnvVar("NAIS_DATABASE_SPINNSYNBACKEND_SPINNSYNDB_USERNAME"),
    val spinnsynBackendDbPassword: String = getEnvVar("NAIS_DATABASE_SPINNSYNBACKEND_SPINNSYNDB_PASSWORD"),
    val spvedtakFrontendUrl: String = getEnvVar("SPVEDTAK_FRONTEND_URL"),
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),
    val oidcWellKnownUri: String = getEnvVar("OIDC_WELLKNOWN_URI"),
    val sidecarInitialDelay: Long = getEnvVar("SIDECAR_INITIAL_DELAY", "5000").toLong(),
    val loginserviceClientId: String = getEnvVar("LOGINSERVICE_CLIENTID")
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
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
