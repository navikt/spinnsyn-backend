package no.nav.helse.flex.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor
import java.util.concurrent.Executors

@Configuration
@EnableAsync
class SchedulerConfig {
    @Bean
    fun fixedThreadPool(): ConcurrentTaskExecutor =
        ConcurrentTaskExecutor(
            Executors.newFixedThreadPool(10),
        )
}
