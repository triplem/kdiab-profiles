package org.javafreedom.kdiab.profiles.application.service

import kotlin.uuid.Uuid
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.profiles.domain.model.Profile
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus
import org.javafreedom.kdiab.profiles.domain.port.ProfileRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class ProfileService(private val profileRepository: ProfileRepository) {

        suspend fun createProfile(profile: Profile): Profile {
                profile.validate()
                logger.debug { "Saving new profile ${profile.id} for user ${profile.userId}" }
                return profileRepository.save(profile)
        }

        suspend fun getProfile(id: Uuid): Profile? = profileRepository.findById(id)

        suspend fun getProfiles(userId: Uuid): List<Profile> =
                profileRepository.findAllByUserId(userId)

        suspend fun getHistory(
                userId: Uuid,
                from: kotlin.time.Instant,
                to: kotlin.time.Instant
        ): List<Profile> = profileRepository.findHistory(userId, from, to)

        suspend fun getActiveProfile(userId: Uuid): Profile? =
                profileRepository.findActiveByUserId(userId)

        suspend fun activateProfile(userId: Uuid, profileId: Uuid): Profile {
                val profile = getProfileOrThrow(profileId)
                checkOwnership(profile, userId)

                if (profile.status == ProfileStatus.ACTIVE) return profile

                // If activating an ARCHIVED profile, clone it to preserve the historical record
                val profileToActivate = if (profile.status == ProfileStatus.ARCHIVED) {
                        profile.copy(
                                id = Uuid.random(),
                                status = ProfileStatus.ACTIVE,
                                createdAt = kotlin.time.Clock.System.now(),
                                previousProfileId = profile.id
                        )
                } else {
                        profile.copy(status = ProfileStatus.ACTIVE)
                }

                // Atomic activation (archives old if exists, activates new)
                val currentActive = profileRepository.findActiveByUserId(userId)
                val oldActive = currentActive?.copy(status = ProfileStatus.ARCHIVED)

                return profileRepository.activateProfile(oldActive, profileToActivate)
        }

        suspend fun acceptProposedProfile(userId: Uuid, profileId: Uuid): Profile {
                val profile = getProfileOrThrow(profileId)
                checkOwnership(profile, userId)

                if (profile.status != ProfileStatus.PROPOSED) {
                        throw org.javafreedom.kdiab.profiles.domain.exception
                                .BusinessValidationException("Only PROPOSED profiles can be accepted")
                }

                val currentActive = profileRepository.findActiveByUserId(userId)
                val oldActive = currentActive?.copy(status = ProfileStatus.ARCHIVED)
                val newActive = profile.copy(status = ProfileStatus.ACTIVE)

                return profileRepository.activateProfile(oldActive, newActive)
        }

        suspend fun rejectProposedProfile(userId: Uuid, profileId: Uuid): Profile {
                val profile = getProfileOrThrow(profileId)
                checkOwnership(profile, userId)

                if (profile.status != ProfileStatus.PROPOSED) {
                        throw org.javafreedom.kdiab.profiles.domain.exception
                                .BusinessValidationException("Only PROPOSED profiles can be rejected")
                }

                return profileRepository.update(profile.copy(status = ProfileStatus.ARCHIVED))
        }

        suspend fun updateProfile(profile: Profile): Profile {
                profile.validate()
                val existing = getProfileOrThrow(profile.id)
                checkOwnership(existing, profile.userId)

                if (existing.status == ProfileStatus.ARCHIVED) {
                        throw org.javafreedom.kdiab.profiles.domain.exception
                                .BusinessValidationException("Cannot update an archived profile")
                }

                if (existing.status == ProfileStatus.ACTIVE) {
                        // Copy-on-Write: Archive existing, save new active version
                        val archived = existing.copy(status = ProfileStatus.ARCHIVED)
                        val newVersion =
                                profile.copy(
                                        id = Uuid.random(),
                                        status = ProfileStatus.ACTIVE,
                                        createdAt = kotlin.time.Clock.System.now(),
                                        previousProfileId = existing.id
                                )
                        logger.debug { 
                                "Copy-on-write UPDATE: Archived ${existing.id}, " + 
                                "created new active ${newVersion.id}" 
                        }
                        return profileRepository.updateActiveProfile(archived, newVersion)
                }

                logger.debug { "Standard update for profile ${profile.id}" }
                return profileRepository.update(profile)
        }

        suspend fun deleteProfile(userId: Uuid, profileId: Uuid): Boolean {
                val profile = getProfileOrThrow(profileId)
                checkOwnership(profile, userId)

                if (profile.status == ProfileStatus.ACTIVE ||
                                profile.status == ProfileStatus.ARCHIVED
                ) {
                        throw org.javafreedom.kdiab.profiles.domain.exception
                                .BusinessValidationException(
                                        "Cannot delete an active or archived profile"
                                )
                }

                return profileRepository.delete(profileId)
        }

        suspend fun deleteAllProfiles(userId: Uuid): Boolean =
                profileRepository.deleteByUserIdAndStatus(userId, ProfileStatus.DRAFT)

        suspend fun deleteSegment(
                userId: Uuid,
                profileId: Uuid,
                segmentType: String,
                startTime: kotlinx.datetime.LocalTime
        ): Profile {
                val profile = getProfileOrThrow(profileId)
                checkOwnership(profile, userId)

                if (profile.status == ProfileStatus.ARCHIVED) {
                        throw org.javafreedom.kdiab.profiles.domain.exception
                                .BusinessValidationException(
                                        "Cannot modify segments of an archived profile"
                                )
                }

                val updatedProfile =
                        when (segmentType.lowercase()) {
                                "basal" ->
                                        profile.copy(
                                                basal =
                                                        profile.basal.filterNot {
                                                                it.startTime == startTime
                                                        }
                                        )
                                "icr" ->
                                        profile.copy(
                                                icr =
                                                        profile.icr.filterNot {
                                                                it.startTime == startTime
                                                        }
                                        )
                                "isf" ->
                                        profile.copy(
                                                isf =
                                                        profile.isf.filterNot {
                                                                it.startTime == startTime
                                                        }
                                        )
                                "targets" ->
                                        profile.copy(
                                                targets =
                                                        profile.targets.filterNot {
                                                                it.startTime == startTime
                                                        }
                                        )
                                else ->
                                        throw IllegalArgumentException(
                                                "Unknown segment type: $segmentType"
                                        )
                        }

                if (profile.status == ProfileStatus.ACTIVE) {
                        // Copy-on-Write: Archive existing, save new active version
                        val archived = profile.copy(status = ProfileStatus.ARCHIVED)
                        val newVersion = updatedProfile.copy(
                                id = Uuid.random(),
                                status = ProfileStatus.ACTIVE,
                                createdAt = kotlin.time.Clock.System.now(),
                                previousProfileId = profile.id
                        )
                        return profileRepository.updateActiveProfile(archived, newVersion)
                }

                return profileRepository.update(updatedProfile)
        }

        private suspend fun getProfileOrThrow(id: Uuid): Profile =
                profileRepository.findById(id)
                        ?: throw org.javafreedom.kdiab.profiles.domain.exception
                                .ResourceNotFoundException("Profile not found")

        private fun checkOwnership(profile: Profile, userId: Uuid) {
                if (profile.userId != userId) {
                        throw org.javafreedom.kdiab.profiles.domain.exception
                                .AuthorizationException("Profile does not belong to user")
                }
        }
}
