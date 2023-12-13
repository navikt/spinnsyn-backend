package no.nav.helse.flex.config

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets

@Configuration
class RestTemplateConfig {
    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplateBuilder().build()
    }

    @Bean
    fun plainTextUtf8RestTemplate(): RestTemplate {
        return RestTemplateBuilder()
            .messageConverters(StringHttpMessageConverter(StandardCharsets.UTF_8))
            .build()
    }
}
