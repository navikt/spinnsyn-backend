package no.nav.helse.flex.testutil

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.helse.flex.db.DatabaseInterface
import org.flywaydb.core.Flyway
import java.net.ServerSocket
import java.sql.Connection

class TestDB : DatabaseInterface {
    private var pg: EmbeddedPostgres? = null
    override val connection: Connection
        get() = pg!!.postgresDatabase.connection.apply { autoCommit = false }

    init {
        pg = EmbeddedPostgres.start()
        Flyway.configure().run {
            dataSource(pg?.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg?.close()
    }
}

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
