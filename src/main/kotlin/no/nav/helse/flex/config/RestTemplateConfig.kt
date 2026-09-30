package no.nav.helse.flex.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets

@Configuration
class RestTemplateConfig {
    @Bean
    fun restTemplate(): RestTemplate = RestTemplateBuilder().build()

    @Bean
    fun plainTextUtf8RestTemplate(): RestTemplate =
        RestTemplateBuilder()
            .messageConverters(StringHttpMessageConverter(StandardCharsets.UTF_8))
            .build()

    @Bean
    @Profile("!test")
    fun texasRestClient(
        @Value("\${NAIS_TOKEN_INTROSPECTION_ENDPOINT}") url: String,
    ): RestClient {
        val requestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(5_000)
                setReadTimeout(10_000)
            }
        return RestClient
            .builder()
            .baseUrl(url)
            .requestFactory(requestFactory)
            .build()
    }
}
