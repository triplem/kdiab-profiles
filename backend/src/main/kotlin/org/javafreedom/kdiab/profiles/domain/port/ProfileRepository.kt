package org.javafreedom.kdiab.profiles.domain.port

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.domain.model.Profile

interface ProfileRepository {
    suspend fun save(profile: Profile): Profile
    suspend fun findById(id: Uuid): Profile?
    suspend fun findAllByUserId(userId: Uuid): List<Profile>
    suspend fun findActiveByUserId(userId: Uuid): Profile?
    suspend fun update(profile: Profile): Profile
    suspend fun findHistory(
            userId: Uuid,
            from: kotlin.time.Instant,
            to: kotlin.time.Instant
    ): List<Profile>
    suspend fun delete(id: Uuid): Boolean
    suspend fun deleteAllByUserId(userId: Uuid): Boolean
    suspend fun deleteByUserIdAndStatus(userId: Uuid, status: org.javafreedom.kdiab.profiles.domain.model.ProfileStatus): Boolean

    // Atomic operations
    suspend fun updateActiveProfile(oldProfile: Profile, newProfile: Profile): Profile
    suspend fun activateProfile(oldActive: Profile?, newActive: Profile): Profile
}
