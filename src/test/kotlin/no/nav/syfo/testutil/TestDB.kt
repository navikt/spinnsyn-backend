package no.nav.syfo.testutil

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import java.net.ServerSocket
import java.sql.Connection
import no.nav.syfo.db.DatabaseInterface
import org.flywaydb.core.Flyway

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
