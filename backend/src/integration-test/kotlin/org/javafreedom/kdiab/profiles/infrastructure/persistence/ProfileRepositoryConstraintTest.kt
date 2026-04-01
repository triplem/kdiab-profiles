@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.javafreedom.kdiab.profiles.domain.model.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.*
import java.sql.DriverManager

class ProfileRepositoryConstraintTest {

    private lateinit var repository: ExposedProfileRepository

    companion object {
        // Singleton H2 instance for the test class
        private const val DB_NAME = "test_constraint_singleton"
        private const val JDBC_URL = "jdbc:h2:mem:$DB_NAME;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
        private const val DRIVER = "org.h2.Driver"
        private const val USER = "root"
        private const val PASSWORD = ""

        init {
            // Register H2 driver and Initialize DB once
            Class.forName(DRIVER)
            
            // Connect Exposed
            Database.connect(
                url = JDBC_URL,
                driver = DRIVER,
                user = USER,
                password = PASSWORD
            )

            // Run Liquibase Migrations ONCE
            val connection = DriverManager.getConnection(JDBC_URL, USER, PASSWORD)
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            val liquibase = Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database
            )
            liquibase.update("")
            println("Liquibase migration completed for singleton DB.")
        }
    }

    @BeforeTest
    fun setup() {
        repository = ExposedProfileRepository()
        // Clean up data before each test
        transaction {
            exec("DELETE FROM profiles")
        }
    }

    @Test
    fun `should fail when inserting second active profile for same user`() {
        println("TEST START: should fail when inserting second active profile")
        val userId = Uuid.random()
        
        // Use direct transaction to verify DB constraint without coroutine complexity
        transaction {
            // 1. Save first active profile
            insertTestProfile(userId, ProfileStatus.ACTIVE)
            println("Saved p1 (Active)")
        
            // 2. Attempt to save second active profile
            // Expectation: H2 in Postgres mode should respect the unique index
             assertFailsWith<Exception> {
                println("Attempting to save p2 (Active)...")
                insertTestProfile(userId, ProfileStatus.ACTIVE)
            }
        }
        println("TEST FINISHED: should fail ...")
    }

    private fun insertTestProfile(userId: Uuid, status: ProfileStatus) {
         Profiles.insert {
            it[id] = Uuid.random()
            it[this.userId] = userId
            it[name] = "Test Profile"
            it[insulinType] = "Fiasp"
            it[units] = "mg/dl"
            it[durationOfAction] = 180
            it[timeZone] = "UTC"
            it[this.status] = status
            it[createdAt] = java.time.Instant.now()
            it[segments] = ProfileSegments(
               basal = listOf(BasalSegment(LocalTime(0,0), 1.0))
            )
        }
    }

    @Test
    fun `should allow multiple archived profiles`() = runBlocking {
        val userId = Uuid.random()

        val p1 = createTestProfile(userId = userId, status = ProfileStatus.ARCHIVED)
        val p2 = createTestProfile(userId = userId, status = ProfileStatus.ARCHIVED)

        repository.save(p1)
        repository.save(p2)
        // Should succeed
    }

    @Test
    fun `should allow multiple draft profiles`() = runBlocking {
        val userId = Uuid.random()

        val p1 = createTestProfile(userId = userId, status = ProfileStatus.DRAFT)
        val p2 = createTestProfile(userId = userId, status = ProfileStatus.DRAFT)

        repository.save(p1)
        repository.save(p2)
        // Should succeed
    }

    private fun createTestProfile(
            userId: Uuid = Uuid.random(),
            status: ProfileStatus = ProfileStatus.DRAFT
    ): Profile {
        return Profile(
                userId = userId,
                name = "Test Profile",
                insulinType = "Fiasp",
                durationOfAction = 180,
                status = status,
                basal = listOf(BasalSegment(LocalTime(0, 0), 0.5)),
                icr = emptyList(),
                isf = emptyList(),
                targets = emptyList()
        )
    }
}
