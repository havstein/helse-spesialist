package no.nav.helse.db

import kotliquery.Session
import kotliquery.sessionOf
import javax.sql.DataSource

interface FleksibelDao {
    fun <T> nySessionEllerTransacation(block: Session.() -> T): T
}

class Flexi(val dataSource: DataSource?, val session: Session? = null) : FleksibelDao {
    init {
        check(dataSource != null || session != null)
    }

    override fun <T : Any?> nySessionEllerTransacation(block: Session.() -> T): T {
        return if (session != null) {
            session.block()
        } else {
            sessionOf(dataSource!!).use { it.block() }
        }
    }
}
