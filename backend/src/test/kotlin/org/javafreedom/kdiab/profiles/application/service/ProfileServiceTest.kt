@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.profiles.domain.model.BasalSegment
import org.javafreedom.kdiab.profiles.domain.model.IcrSegment
import org.javafreedom.kdiab.profiles.domain.model.IsfSegment
import org.javafreedom.kdiab.profiles.domain.model.Profile
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus
import org.javafreedom.kdiab.profiles.domain.model.TargetSegment
import org.javafreedom.kdiab.profiles.domain.repository.ProfileRepository

class ProfileServiceTest {

        private val repository = mockk<ProfileRepository>()
        private val service = ProfileService(repository)

        @Test
        fun `createProfile should save and return profile`() = runBlocking {
                val userId = Uuid.random()
                val profile =
                        Profile(
                                userId = userId,
                                name = "Test Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.save(any()) } returns profile

                val result = service.createProfile(profile)

                assertEquals(profile, result)
                coVerify(exactly = 1) { repository.save(profile) }
        }

        @Test
        fun `getProfile should return profile when found`() = runBlocking {
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = Uuid.random(),
                                name = "Test Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile

                val result = service.getProfile(profileId)

                assertEquals(profile, result)
        }

        @Test
        fun `getProfiles should return list of profiles`() = runBlocking {
                val userId = Uuid.random()
                val profiles =
                        listOf(
                                Profile(
                                        userId = userId,
                                        name = "Profile 1",
                                        insulinType = "Fiasp",
                                        durationOfAction = 180,
                                        status = ProfileStatus.DRAFT,
                                        basal = emptyList(),
                                        icr = emptyList(),
                                        isf = emptyList(),
                                        targets = emptyList()
                                )
                        )

                coEvery { repository.findAllByUserId(userId) } returns profiles

                val result = service.getProfiles(userId)

                assertEquals(profiles, result)
        }

        @Test
        fun `getActiveProfile should return active profile`() = runBlocking {
                val userId = Uuid.random()
                val profile =
                        Profile(
                                userId = userId,
                                name = "Active Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.ACTIVE,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findActiveByUserId(userId) } returns profile

                val result = service.getActiveProfile(userId)

                assertEquals(profile, result)
        }

        @Test
        fun `activateProfile should archive current active and activate new profile`() =
                runBlocking {
                        val userId = Uuid.random()
                        val oldActiveId = Uuid.random()
                        val newActiveId = Uuid.random()

                        val oldActive =
                                Profile(
                                        id = oldActiveId,
                                        userId = userId,
                                        name = "Old Active",
                                        insulinType = "Fiasp",
                                        durationOfAction = 180,
                                        status = ProfileStatus.ACTIVE,
                                        basal = emptyList(),
                                        icr = emptyList(),
                                        isf = emptyList(),
                                        targets = emptyList()
                                )

                        val newProfile =
                                Profile(
                                        id = newActiveId,
                                        userId = userId,
                                        name = "New Profile",
                                        insulinType = "Fiasp",
                                        durationOfAction = 180,
                                        status = ProfileStatus.DRAFT,
                                        basal = emptyList(),
                                        icr = emptyList(),
                                        isf = emptyList(),
                                        targets = emptyList()
                                )

                        coEvery { repository.findById(newActiveId) } returns newProfile
                        coEvery { repository.findActiveByUserId(userId) } returns oldActive

                        val expectedNewActive = newProfile.copy(status = ProfileStatus.ACTIVE)
                        coEvery { repository.activateProfile(any(), any()) } returns
                                expectedNewActive

                        val result = service.activateProfile(userId, newActiveId)

                        assertNotNull(result)
                        assertEquals(ProfileStatus.ACTIVE, result.status)

                        coVerify(exactly = 1) {
                                repository.activateProfile(
                                        match {
                                                it.id == oldActiveId &&
                                                        it.status == ProfileStatus.ARCHIVED
                                        },
                                        match {
                                                it.id == newActiveId &&
                                                        it.status == ProfileStatus.ACTIVE
                                        }
                                )
                        }
                }

        @Test
        fun `activateProfile throws exception if profile not found`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()

                coEvery { repository.findById(profileId) } returns null

                assertFailsWith<
                        org.javafreedom.kdiab.profiles.domain.exception.ResourceNotFoundException> {
                        service.activateProfile(userId, profileId)
                }
        }

        @Test
        fun `activateProfile throws exception if profile belongs to other user`() = runBlocking {
                val userId = Uuid.random()
                val otherUser = Uuid.random()
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = otherUser,
                                name = "Test",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<
                        org.javafreedom.kdiab.profiles.domain.exception.AuthorizationException> {
                        service.activateProfile(userId, profileId)
                }
        }

	@Test
	fun `activateProfile should clone archived profile when activated`() = runBlocking {
                val userId = Uuid.random()
                val archivedId = Uuid.random()
                val archivedProfile = Profile(
                        id = archivedId,
                        userId = userId,
                        name = "Archived",
                        status = ProfileStatus.ARCHIVED,
                        basal = emptyList(), icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )

                coEvery { repository.findById(archivedId) } returns archivedProfile
                coEvery { repository.findActiveByUserId(userId) } returns null
                coEvery { repository.activateProfile(any(), any()) } answers { secondArg() }

                val result = service.activateProfile(userId, archivedId)

                assertEquals(ProfileStatus.ACTIVE, result.status)
                assert(result.id != archivedId)
                assertEquals(archivedId, result.previousProfileId)
                coVerify { repository.activateProfile(null, match { it.previousProfileId == archivedId }) }
        }

        @Test
        fun `activateProfile returns immediately if already active`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Test",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.ACTIVE,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile

                val result = service.activateProfile(userId, profileId)
                assertEquals(profile, result)
                coVerify(exactly = 0) { repository.activateProfile(any(), any()) }
        }

        @Test
        fun `acceptProposedProfile happy path`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Proposed", status = ProfileStatus.PROPOSED,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.findActiveByUserId(userId) } returns null
                coEvery { repository.activateProfile(any(), any()) } answers { secondArg() }

                val result = service.acceptProposedProfile(userId, profileId)

                assertEquals(ProfileStatus.ACTIVE, result.status)
                assertEquals(profileId, result.id)
                coVerify {
                        repository.activateProfile(null, match {
                                it.id == profileId && it.status == ProfileStatus.ACTIVE
                        })
                }
        }

        @Test
        fun `acceptProposedProfile throws if not proposed`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Draft", status = ProfileStatus.DRAFT,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.acceptProposedProfile(userId, profileId)
                }
        }

        @Test
        fun `rejectProposedProfile happy path`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Proposed", status = ProfileStatus.PROPOSED,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.update(any()) } answers { firstArg() }

                val result = service.rejectProposedProfile(userId, profileId)

                assertEquals(ProfileStatus.ARCHIVED, result.status)
                coVerify { repository.update(match { it.id == profileId && it.status == ProfileStatus.ARCHIVED }) }
        }

        @Test
        fun `rejectProposedProfile throws if not proposed`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Draft", status = ProfileStatus.DRAFT,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.rejectProposedProfile(userId, profileId)
                }
        }

        @Test
        fun `deleteProfile happy path`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Test",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.delete(profileId) } returns true

                val result = service.deleteProfile(userId, profileId)
                assert(result)
                coVerify(exactly = 1) { repository.delete(profileId) }
        }

        @Test
        fun `deleteProfile throws if active or archived`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Active", status = ProfileStatus.ACTIVE,
                        basal = emptyList(), icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.deleteProfile(userId, profileId)
                }
        }

        @Test
        fun `deleteProfile throws if not found`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                coEvery { repository.findById(profileId) } returns null
                assertFailsWith<
                        org.javafreedom.kdiab.profiles.domain.exception.ResourceNotFoundException> {
                        service.deleteProfile(userId, profileId)
                }
        }

        @Test
        fun `deleteSegment logic works`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val time = LocalTime(0, 0)
                val basal = listOf(BasalSegment(time, 1.0))
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Test",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = basal,
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.update(any()) } answers { firstArg() }

                val result = service.deleteSegment(userId, profileId, "basal", time)
                assert(result.basal.isEmpty())
        }

        @Test
        fun `deleteSegment logic works with copy-on-write for ACTIVE profile`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val time = LocalTime(0, 0)
                val basal = listOf(BasalSegment(time, 1.0))
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Active",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.ACTIVE,
                                basal = basal,
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.updateActiveProfile(any(), any()) } answers { secondArg() }

                val result = service.deleteSegment(userId, profileId, "basal", time)
                assert(result.basal.isEmpty())
                assertEquals(ProfileStatus.ACTIVE, result.status)
                assert(result.id != profileId)
                coVerify { repository.updateActiveProfile(match { it.status == ProfileStatus.ARCHIVED }, any()) }
        }

        @Test
        fun `deleteSegment throws if archived`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Archived", status = ProfileStatus.ARCHIVED,
                        basal = emptyList(), icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.deleteSegment(userId, profileId, "basal", LocalTime(0, 0))
                }
        }

        @Test
        fun `updateProfile delegates to repository`() = runBlocking {
                val userId = Uuid.random()
                val profile =
                        Profile(
                                userId = userId,
                                name = "Test Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profile.id) } returns profile
                coEvery { repository.update(profile) } returns profile
                val result = service.updateProfile(profile)
                assertEquals(profile, result)
        }

        @Test
        fun `updateProfile throws if archived`() = runBlocking {
                val profile = Profile(
                        userId = Uuid.random(), name = "Archived", status = ProfileStatus.ARCHIVED,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profile.id) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.updateProfile(profile)
                }
        }

        @Test
        fun `updateProfile copy-on-write if active`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Active", status = ProfileStatus.ACTIVE,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.updateActiveProfile(any(), any()) } answers { secondArg() }

                val updated = profile.copy(name = "Updated Active")
                val result = service.updateProfile(updated)

                assertEquals("Updated Active", result.name)
                assertEquals(ProfileStatus.ACTIVE, result.status)
                assert(result.id != profileId)
                coVerify { repository.updateActiveProfile(match { it.status == ProfileStatus.ARCHIVED }, any()) }
        }

        @Test
        fun `deleteAllProfiles delegates to repository`() = runBlocking {
                val userId = Uuid.random()
                coEvery { repository.deleteByUserIdAndStatus(userId, ProfileStatus.DRAFT) } returns true
                val result = service.deleteAllProfiles(userId)
                assert(result)
        }

        @Test
        fun `deleteSegment unknown type throws exception`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Test",
                                status = ProfileStatus.DRAFT,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList(),
                                insulinType = "Fiasp",
                                durationOfAction = 180
                        )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<IllegalArgumentException> {
                        service.deleteSegment(userId, profileId, "unknown", LocalTime(0, 0))
                }
        }

        @Test
        fun `deleteSegment helper covers other types`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val time = LocalTime(0, 0)
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Test",
                                status = ProfileStatus.DRAFT,
                                basal = emptyList(),
                                icr = listOf(IcrSegment(time, 10.0)),
                                isf = listOf(IsfSegment(time, 20.0)),
                                targets = listOf(TargetSegment(time, 100.0, 100.0)),
                                insulinType = "Fiasp",
                                durationOfAction = 180
                        )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.update(any()) } answers { firstArg() }

                // ICR
                val res1 = service.deleteSegment(userId, profileId, "icr", time)
                assert(res1.icr.isEmpty())

                // ISF
                val res2 = service.deleteSegment(userId, profileId, "isf", time)
                assert(res2.isf.isEmpty())

                // Targets
                val res3 = service.deleteSegment(userId, profileId, "targets", time)
                assert(res3.targets.isEmpty())
        }
}
