package no.nav.helse.flex

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableScheduling
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

@SpringBootApplication
@EnableKafka
@EnableJwtTokenValidation
@EnableResilientMethods
class Application

@Profile("default")
@Configuration
@EnableScheduling
class DeployApplicationConfig

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

val objectMapper: ObjectMapper =
    JsonMapper
        .builder()
        .addModule(kotlinModule())
        .build()

fun Any.serialisertTilString(): String = objectMapper.writeValueAsString(this)
