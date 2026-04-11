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
        assertEquals(profile.id, retrieved.id)
        assertEquals(profile.name, retrieved.name)
        assertEquals(1, retrieved.basal.size)
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
        assertEquals("Active", active.name)
    }

    @Test
    fun `update should modify existing profile`() = runBlocking {
        val profile = createTestProfile()
        repository.save(profile)

        val updated = profile.copy(name = "Updated Name", status = ProfileStatus.ACTIVE)
        repository.update(updated)

        val retrieved = repository.findById(profile.id)
        assertNotNull(retrieved)
        assertEquals("Updated Name", retrieved.name)
        assertEquals(ProfileStatus.ACTIVE, retrieved.status)
    }

    // ── activateProfile: DRAFT→ACTIVE (existing row → update) ─────────────────
    @Test
    fun `activateProfile should activate a DRAFT profile via update`() = runBlocking {
        val userId = Uuid.random()
        val draft = createTestProfile(userId = userId, status = ProfileStatus.DRAFT)
        repository.save(draft)

        val toActivate = draft.copy(status = ProfileStatus.ACTIVE)
        val result = repository.activateProfile(null, toActivate)

        assertEquals(ProfileStatus.ACTIVE, result.status)
        val retrieved = repository.findById(draft.id)
        assertNotNull(retrieved)
        assertEquals(ProfileStatus.ACTIVE, retrieved.status)
    }

    // ── activateProfile: ARCHIVED→ACTIVE clone (new UUID → insert) ────────────
    @Test
    fun `activateProfile should insert a cloned ARCHIVED profile with new id`() = runBlocking {
        val userId = Uuid.random()
        val archived = createTestProfile(userId = userId, status = ProfileStatus.ARCHIVED)
        repository.save(archived)

        // Simulate the service's copy: brand-new UUID, pointing back to archived
        val cloned = archived.copy(
            id = Uuid.random(),
            status = ProfileStatus.ACTIVE,
            previousProfileId = archived.id
        )
        val result = repository.activateProfile(null, cloned)

        assertEquals(cloned.id, result.id)
        assertEquals(ProfileStatus.ACTIVE, result.status)
        val retrieved = repository.findById(cloned.id)
        assertNotNull(retrieved)
        assertEquals(archived.id, retrieved.previousProfileId)
        // Original archived record must still exist
        val originalStillExists = repository.findById(archived.id)
        assertNotNull(originalStillExists)
        assertEquals(ProfileStatus.ARCHIVED, originalStillExists.status)
    }

    // ── activateProfile: archives old active, inserts new ─────────────────────
    @Test
    fun `activateProfile should archive old active when activating a new profile`() = runBlocking {
        val userId = Uuid.random()
        val currentActive = createTestProfile(userId = userId, name = "Current", status = ProfileStatus.ACTIVE)
        val draft = createTestProfile(userId = userId, name = "New", status = ProfileStatus.DRAFT)
        repository.save(currentActive)
        repository.save(draft)

        val oldArchived = currentActive.copy(status = ProfileStatus.ARCHIVED)
        val newActive = draft.copy(status = ProfileStatus.ACTIVE)
        repository.activateProfile(oldArchived, newActive)

        assertEquals(ProfileStatus.ARCHIVED, repository.findById(currentActive.id)?.status)
        assertEquals(ProfileStatus.ACTIVE, repository.findById(draft.id)?.status)
        assertEquals(null, repository.findActiveByUserId(userId)?.let {
            if (it.id == currentActive.id) "old still active" else null
        })
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
