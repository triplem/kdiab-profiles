package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.*

class ExposedInsulinRepositoryTest {

    private lateinit var repository: ExposedInsulinRepository

    companion object {
        init {
            Database.connect(
                url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                driver = "org.h2.Driver",
                user = "root",
                password = ""
            )
        }
    }

    @BeforeTest
    fun setup() {
        transaction { SchemaUtils.create(Insulins) }
        repository = ExposedInsulinRepository()
    }

    @AfterTest
    fun tearDown() {
        transaction { SchemaUtils.drop(Insulins) }
    }

    @Test
    fun `findAll should return empty initially`() = runBlocking {
        val results = repository.findAll()
        assertEquals(0, results.size)
    }

    @Test
    fun `create should add insulin and findAll should return it`() = runBlocking {
        val created = repository.create("Novolog")
        assertNotNull(created.id)
        assertEquals("Novolog", created.name)

        val results = repository.findAll()
        assertEquals(1, results.size)
        assertEquals("Novolog", results[0].name)
    }
}
