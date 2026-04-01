@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlinx.datetime.Clock
import org.javafreedom.kdiab.profiles.api.models.CreateProfileRequest
import org.javafreedom.kdiab.profiles.domain.model.Profile as DomainProfile
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus

class ProfileMapperTest {

        @Test
        fun `toDomain should map request to domain profile`() {
                val userId = Uuid.random()
                val request =
                        CreateProfileRequest(
                                name = "Test Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180
                        )

                val domain = request.toDomain(userId)

                assertEquals("Test Profile", domain.name)
                assertEquals("Fiasp", domain.insulinType)
                assertEquals(180, domain.durationOfAction)
                assertEquals(userId, domain.userId)
                assertEquals(ProfileStatus.DRAFT, domain.status)
        }

        @Test
        fun `toApi should map domain profile to api profile`() {
                val id = Uuid.random()
                val userId = Uuid.random()
                val now = Clock.System.now()
                val domain =
                        DomainProfile(
                                id = id,
                                userId = userId,
                                name = "Test Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.ACTIVE,
                                createdAt = now,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                val api = domain.toApi()

                assertEquals(id.toString(), api.id)
                assertEquals(userId.toString(), api.userId)
                assertEquals("Test Profile", api.name)
                assertEquals("ACTIVE", api.status.value)
        }
}
