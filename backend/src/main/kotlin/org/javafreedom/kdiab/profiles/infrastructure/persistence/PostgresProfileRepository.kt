package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.domain.model.*
import org.javafreedom.kdiab.profiles.domain.port.ProfileRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PostgresProfileRepository : ProfileRepository {

    override suspend fun save(profile: Profile): Profile = transaction {
        insertProfileInTx(profile)
    }

    override suspend fun findHistory(
            userId: Uuid,
            from: kotlin.time.Instant,
            to: kotlin.time.Instant
    ): List<Profile> = transaction {
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

    override suspend fun findById(id: Uuid): Profile? = transaction {
        Profiles.selectAll()
                .where { Profiles.id eq id }
                .map { mapToProfile(it) }
                .singleOrNull()
    }

    override suspend fun findAllByUserId(userId: Uuid): List<Profile> = transaction {
        Profiles.selectAll()
                .where { Profiles.userId eq userId }
                .orderBy(Profiles.createdAt, SortOrder.DESC)
                .map { mapToProfile(it) }
    }

    override suspend fun findActiveByUserId(userId: Uuid): Profile? = transaction {
        Profiles.selectAll()
                .where {
                    (Profiles.userId eq userId) and
                            (Profiles.status eq ProfileStatus.ACTIVE)
                }
                .map { mapToProfile(it) }
                .singleOrNull()
    }

    override suspend fun update(profile: Profile): Profile = transaction {
        updateProfileInTx(profile)
    }

    private fun mapToProfile(row: ResultRow): Profile {
        val segments = row[Profiles.segments]
        return Profile(
                id = row[Profiles.id],
                userId = row[Profiles.userId],
                previousProfileId = row[Profiles.previousProfileId],
                name = row[Profiles.name],
                insulinType = row[Profiles.insulinType],
                units = row[Profiles.units],
                durationOfAction = row[Profiles.durationOfAction],
                timeZone = kotlinx.datetime.TimeZone.of(row[Profiles.timeZone]),
                status = row[Profiles.status],
                createdAt = kotlin.time.Instant.fromEpochMilliseconds(row[Profiles.createdAt].toEpochMilli()),
                basal = segments.basal,
                icr = segments.icr,
                isf = segments.isf,
                targets = segments.targets
        )
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        Profiles.update({ Profiles.id eq id }) {
            it[status] = ProfileStatus.ARCHIVED
        } > 0
    }

    override suspend fun deleteAllByUserId(userId: Uuid): Boolean = transaction {
        Profiles.deleteWhere { Profiles.userId eq userId } > 0
    }

    override suspend fun deleteByUserIdAndStatus(userId: Uuid, status: ProfileStatus): Boolean =
            transaction {
                Profiles.deleteWhere {
                    (Profiles.userId eq userId) and (Profiles.status eq status)
                } > 0
            }

    override suspend fun updateActiveProfile(oldProfile: Profile, newProfile: Profile): Profile =
            transaction {
                // 1. Update old profile (Archive)
                updateProfileInTx(oldProfile)
                // 2. Save new profile (Active)
                insertProfileInTx(newProfile)
            }

    override suspend fun activateProfile(oldActive: Profile?, newActive: Profile): Profile =
            transaction {
                // 1. Archive old active if exists
                oldActive?.let { updateProfileInTx(it) }
                // 2. Update new profile to be Active
                updateProfileInTx(newActive)
            }

    private fun insertProfileInTx(profile: Profile): Profile {
        val segments =
                ProfileSegments(
                        basal = profile.basal,
                        icr = profile.icr,
                        isf = profile.isf,
                        targets = profile.targets
                )

        Profiles.insert {
            it[id] = profile.id
            it[userId] = profile.userId
            it[previousProfileId] = profile.previousProfileId
            it[name] = profile.name
            it[insulinType] = profile.insulinType
            it[units] = profile.units
            it[durationOfAction] = profile.durationOfAction
            it[timeZone] = profile.timeZone.id
            it[status] = profile.status
            it[createdAt] = java.time.Instant.ofEpochMilli(profile.createdAt.toEpochMilliseconds())
            it[this.segments] = segments
        }
        return profile
    }

    private fun updateProfileInTx(profile: Profile): Profile {
        val segments =
                ProfileSegments(
                        basal = profile.basal,
                        icr = profile.icr,
                        isf = profile.isf,
                        targets = profile.targets
                )

        Profiles.update({ Profiles.id eq profile.id }) {
            it[previousProfileId] = profile.previousProfileId
            it[name] = profile.name
            it[insulinType] = profile.insulinType
            it[units] = profile.units
            it[durationOfAction] = profile.durationOfAction
            it[timeZone] = profile.timeZone.id
            it[status] = profile.status
            it[this.segments] = segments
        }
        return profile
    }
}
