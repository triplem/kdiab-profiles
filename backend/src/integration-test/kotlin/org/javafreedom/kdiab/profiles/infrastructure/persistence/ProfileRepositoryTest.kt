package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.profiles.domain.model.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.*

@OptIn(ExperimentalUuidApi::class)
class ProfileRepositoryTest {

    private lateinit var repository: ExposedProfileRepository

    companion object {
        init {
            // Connect to H2 in-memory database with PostgreSQL compatibility
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
        transaction {
            SchemaUtils.create(Profiles)
        }
        repository = ExposedProfileRepository()
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(Profiles)
        }
    }

    @Test
    fun `save should persist profile`() = runBlocking {
        val profile = createTestProfile()
        repository.save(profile)

        val retrieved = repository.findById(profile.id)
        assertNotNull(retrieved)
        assertEquals(profile.id, retrieved?.id)
        assertEquals(profile.name, retrieved?.name)
        assertEquals(1, retrieved?.basal?.size)
    }

    @Test
    fun `findAllByUserId should return all profiles for user`() = runBlocking {
        val userId = Uuid.random()
        val p1 = createTestProfile(userId = userId, name = "P1")
        val p2 = createTestProfile(userId = userId, name = "P2")
        val p3 = createTestProfile(userId = Uuid.random(), name = "P3") // Different user

        repository.save(p1)
        repository.save(p2)
        repository.save(p3)

        val results = repository.findAllByUserId(userId)
        assertEquals(2, results.size)
    }

    @Test
    fun `findActiveByUserId should return only active profile`() = runBlocking {
        val userId = Uuid.random()
        val p1 = createTestProfile(userId = userId, status = ProfileStatus.DRAFT)
        val p2 = createTestProfile(userId = userId, status = ProfileStatus.ACTIVE, name = "Active")
        val p3 = createTestProfile(userId = userId, status = ProfileStatus.ARCHIVED)

        repository.save(p1)
        repository.save(p2)
        repository.save(p3)

        val active = repository.findActiveByUserId(userId)
        assertNotNull(active)
        assertEquals("Active", active?.name)
    }

    @Test
    fun `update should modify existing profile`() = runBlocking {
        val profile = createTestProfile()
        repository.save(profile)

        val updated = profile.copy(name = "Updated Name", status = ProfileStatus.ACTIVE)
        repository.update(updated)

        val retrieved = repository.findById(profile.id)
        assertEquals("Updated Name", retrieved?.name)
        assertEquals(ProfileStatus.ACTIVE, retrieved?.status)
    }

    private fun createTestProfile(
        userId: Uuid = Uuid.random(),
        name: String = "Test Profile",
        status: ProfileStatus = ProfileStatus.DRAFT
    ): Profile {
        return Profile(
            userId = userId,
            name = name,
            insulinType = "Fiasp",
            durationOfAction = 180,
            status = status,
            basal = listOf(BasalSegment(LocalTime(0, 0), 0.5)),
            icr = listOf(IcrSegment(LocalTime(0, 0), 15.0)),
            isf = listOf(IsfSegment(LocalTime(0, 0), 40.0)),
            targets = listOf(TargetSegment(LocalTime(0, 0), 100.0, 110.0))
        )
    }
}
