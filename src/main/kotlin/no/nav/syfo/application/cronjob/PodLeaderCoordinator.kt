package no.nav.syfo.application.cronjob

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.log
import java.net.InetAddress

@KtorExperimentalAPI
class PodLeaderCoordinator(
    private val env: Environment
) {

    private val dont_look_for_leader = "dont_look_for_leader"

    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

    @KtorExperimentalAPI
    private val client = HttpClient {}

    fun isLeader(): Boolean {
        val url = env.electorPath
        return when (url == dont_look_for_leader) {
            true -> {
                log.info("Opting not to look for leader, returning true, elector url was:$url")
                true
            }
            else ->
                try {
                    val httpPath = getHttpPath(url)
                    log.info("Looking for leader at url:$httpPath")
                    runBlocking {
                        val response = client.get<String>(urlString = httpPath)
                        val leader: Leader = objectMapper.readValue(response)
                        val hostname: String = InetAddress.getLocalHost().hostName
                        when (hostname == leader.name) {
                            true -> {
                                log.info("Pod with $hostname is the leader")
                                true
                            }
                            else -> {
                                log.info("Pod with $hostname is not leader, leader is:${leader.name}")
                                false
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.error("Exception caught while looking for leader, exception:$e")
                    false
                }
        }
    }

    private fun getHttpPath(url: String): String =
        when (url.startsWith("http://")) {
            true -> url
            else -> "http://$url"
        }

    private data class Leader(val name: String)
}
