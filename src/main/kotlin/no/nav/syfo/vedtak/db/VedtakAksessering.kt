package no.nav.syfo.vedtak.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import java.sql.Connection
import java.sql.ResultSet

fun Connection.hentVedtak(fnr: String): List<Any> =
    this.prepareStatement(
        """
            SELECT vedtak
            FROM  vedtak
            WHERE fnr = ?;
            """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toSykmelding() }
    }

fun ResultSet.toSykmelding(): Any =
    objectMapper.readValue(getString("vedtak"))
