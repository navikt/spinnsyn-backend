package no.nav.helse.flex

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.awaitility.Awaitility.await
import java.time.Duration

fun <K, V> Consumer<K, V>.subscribeHvisIkkeSubscribed(vararg topics: String) {
    if (this.subscription().isEmpty()) {
        this.subscribe(listOf(*topics))
    }
}

fun <K, V> Consumer<K, V>.hentProduserteRecords(duration: Duration = Duration.ofMillis(500)): List<ConsumerRecord<K, V>> =
    this.poll(duration).also { this.commitSync() }.iterator().asSequence().toList()

fun <K, V> Consumer<K, V>.ventPåRecords(
    antall: Int,
    duration: Duration = Duration.ofSeconds(2)
): List<ConsumerRecord<K, V>> {
    val factory = if (antall == 0) {
        // Må vente fullt ut, ikke opp til en tid siden vi vil se at ingen blir produsert
        await().during(duration)
    } else {
        await().atMost(duration)
    }

    val alle = ArrayList<ConsumerRecord<K, V>>()
    factory.until {
        alle.addAll(this.hentProduserteRecords())
        alle.size == antall
    }
    return alle
}
