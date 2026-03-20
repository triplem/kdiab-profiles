package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.javafreedom.kdiab.profiles.domain.model.Insulin
import org.javafreedom.kdiab.profiles.domain.repository.InsulinRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.uuid.Uuid

class ExposedInsulinRepository : InsulinRepository {

    override suspend fun findAll(): List<Insulin> = withContext(Dispatchers.IO) {
        suspendTransaction {
            Insulins.selectAll().map {
                Insulin(
                    id = it[Insulins.id],
                    name = it[Insulins.name]
                )
            }
        }
    }

    override suspend fun create(name: String): Insulin = withContext(Dispatchers.IO) {
        suspendTransaction {
            val newId = Uuid.random()
            Insulins.insert {
                it[id] = newId
                it[Insulins.name] = name
            }
            Insulin(id = newId, name = name)
        }
    }

    override suspend fun update(id: Uuid, name: String): Insulin? = withContext(Dispatchers.IO) {
        suspendTransaction {
            val updated = Insulins.update({ Insulins.id eq id }) {
                it[Insulins.name] = name
            }
            if (updated > 0) Insulin(id = id, name = name) else null
        }
    }

    override suspend fun delete(id: Uuid): Boolean = withContext(Dispatchers.IO) {
        suspendTransaction {
            Insulins.deleteWhere { Insulins.id eq id } > 0
        }
    }
}
