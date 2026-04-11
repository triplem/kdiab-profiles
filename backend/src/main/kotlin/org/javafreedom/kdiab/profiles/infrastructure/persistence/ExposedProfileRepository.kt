@file:Suppress("WildcardImport", "MagicNumber", "MaxLineLength", "TooManyFunctions")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.infrastructure.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.profiles.domain.model.*
import org.javafreedom.kdiab.profiles.domain.repository.ProfileRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

@Serializable
data class ProfileSegments(
    val basal: List<BasalSegment> = emptyList(),
    val icr: List<IcrSegment> = emptyList(),
    val isf: List<IsfSegment> = emptyList(),
    val targets: List<TargetSegment> = emptyList()
)

object Profiles : Table("profiles") {
    private const val NAME_LENGTH = 255
    private const val INSULIN_TYPE_LENGTH = 100
    private const val UNITS_LENGTH = 20

    val id = uuid("id")
    val userId = uuid("user_id")
    val previousProfileId = uuid("previous_profile_id").references(id).nullable()
    val name = varchar("name", NAME_LENGTH)
    val insulinType = varchar("insulin_type", INSULIN_TYPE_LENGTH)
    val units = varchar("units", UNITS_LENGTH).default("mg/dl")
    val durationOfAction = integer("duration_of_action")
    val timeZone = varchar("time_zone", 50)
    val status = enumerationByName("status", 50, ProfileStatus::class)
    val createdAt = timestamp("created_at")
    val segments = jsonb<ProfileSegments>("segments", Json.Default)

    override val primaryKey = PrimaryKey(id)

    init {
        index(
            customIndexName = "IDX_PROFILES_USER_ACTIVE",
            isUnique = true,
            columns = arrayOf(userId),
            filterCondition = { status eq ProfileStatus.ACTIVE }
        )
    }
}

class ExposedProfileRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProfileRepository {

    override suspend fun save(profile: Profile): Profile = withContext(ioDispatcher) {
        suspendTransaction { insertProfileInTx(profile) }
    }

    override suspend fun findHistory(
        userId: Uuid,
        from: Instant,
        to: Instant
    ): List<Profile> = withContext(ioDispatcher) {
        suspendTransaction {
            Profiles.selectAll()
                .where {
                    (Profiles.userId eq userId) and
                            (Profiles.status eq ProfileStatus.ARCHIVED) and
                            (Profiles.createdAt greaterEq
                                    java.time.Instant.ofEpochMilli(from.toEpochMilliseconds())) and
                            (Profiles.createdAt lessEq
                                    java.time.Instant.ofEpochMilli(to.toEpochMilliseconds()))
                }
                .orderBy(Profiles.createdAt, SortOrder.DESC)
                .map { mapToProfile(it) }
        }
    }

    override suspend fun findById(id: Uuid): Profile? = withContext(ioDispatcher) {
        suspendTransaction {
            Profiles.selectAll()
                .where { Profiles.id eq id }
                .map { mapToProfile(it) }
                .singleOrNull()
        }
    }

    override suspend fun findAllByUserId(userId: Uuid): List<Profile> = withContext(ioDispatcher) {
        suspendTransaction {
            Profiles.selectAll()
                .where { Profiles.userId eq userId }
                .orderBy(Profiles.createdAt, SortOrder.DESC)
                .map { mapToProfile(it) }
        }
    }

    override suspend fun findActiveByUserId(userId: Uuid): Profile? = withContext(ioDispatcher) {
        suspendTransaction {
            Profiles.selectAll()
                .where {
                    (Profiles.userId eq userId) and
                            (Profiles.status eq ProfileStatus.ACTIVE)
                }
                .map { mapToProfile(it) }
                .singleOrNull()
        }
    }

    override suspend fun update(profile: Profile): Profile = withContext(ioDispatcher) {
        suspendTransaction { updateProfileInTx(profile) }
    }

    private fun mapToProfile(row: ResultRow): Profile {
        val pSectors: ProfileSegments = row[Profiles.segments]
        val timeZone = try {
            TimeZone.of(row[Profiles.timeZone])
        } catch (e: IllegalTimeZoneException) {
            logger.warn(e) { "Unknown timezone '${row[Profiles.timeZone]}' for profile ${row[Profiles.id]}, falling back to UTC" }
            TimeZone.UTC
        }
        return Profile(
            id = row[Profiles.id],
            userId = row[Profiles.userId],
            previousProfileId = row[Profiles.previousProfileId],
            name = row[Profiles.name],
            insulinType = row[Profiles.insulinType],
            units = row[Profiles.units],
            durationOfAction = row[Profiles.durationOfAction],
            timeZone = timeZone,
            status = row[Profiles.status],
            createdAt = Instant.fromEpochMilliseconds(row[Profiles.createdAt].toEpochMilli()),
            basal = pSectors.basal,
            icr = pSectors.icr,
            isf = pSectors.isf,
            targets = pSectors.targets
        )
    }

    override suspend fun delete(id: Uuid): Boolean = withContext(ioDispatcher) {
        suspendTransaction {
            Profiles.update({ Profiles.id eq id }) {
                it[Profiles.status] = ProfileStatus.ARCHIVED
            } > 0
        }
    }

    override suspend fun deleteByUserIdAndStatus(
        userId: Uuid,
        status: ProfileStatus
    ): Boolean = withContext(ioDispatcher) {
        suspendTransaction {
            Profiles.deleteWhere {
                (Profiles.userId eq userId) and (Profiles.status eq status)
            } > 0
        }
    }

    override suspend fun updateActiveProfile(oldProfile: Profile, newProfile: Profile): Profile =
        withContext(ioDispatcher) {
            suspendTransaction {
                updateProfileInTx(oldProfile)
                insertProfileInTx(newProfile)
            }
        }

    override suspend fun activateProfile(oldActive: Profile?, newActive: Profile): Profile =
        withContext(ioDispatcher) {
            suspendTransaction {
                oldActive?.let { updateProfileInTx(it) }
                val exists = Profiles.selectAll().where { Profiles.id eq newActive.id }.count() > 0
                if (exists) updateProfileInTx(newActive) else insertProfileInTx(newActive)
            }
        }

    private fun insertProfileInTx(profile: Profile): Profile {
        val profileSectors =
            ProfileSegments(
                basal = profile.basal,
                icr = profile.icr,
                isf = profile.isf,
                targets = profile.targets
            )

        Profiles.insert {
            it[Profiles.id] = profile.id
            it[Profiles.userId] = profile.userId
            it[Profiles.previousProfileId] = profile.previousProfileId
            it[Profiles.name] = profile.name
            it[Profiles.insulinType] = profile.insulinType
            it[Profiles.units] = profile.units
            it[Profiles.durationOfAction] = profile.durationOfAction
            it[Profiles.timeZone] = profile.timeZone.id
            it[Profiles.status] = profile.status
            it[Profiles.createdAt] = java.time.Instant.ofEpochMilli(profile.createdAt.toEpochMilliseconds())
            it[Profiles.segments] = profileSectors
        }
        return profile
    }

    private fun updateProfileInTx(profile: Profile): Profile {
        val profileSectors =
            ProfileSegments(
                basal = profile.basal,
                icr = profile.icr,
                isf = profile.isf,
                targets = profile.targets
            )

        Profiles.update({ Profiles.id eq profile.id }) {
            it[Profiles.previousProfileId] = profile.previousProfileId
            it[Profiles.name] = profile.name
            it[Profiles.insulinType] = profile.insulinType
            it[Profiles.units] = profile.units
            it[Profiles.durationOfAction] = profile.durationOfAction
            it[Profiles.timeZone] = profile.timeZone.id
            it[Profiles.status] = profile.status
            it[Profiles.segments] = profileSectors
        }
        return profile
    }
}
