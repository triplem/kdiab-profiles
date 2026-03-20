package org.javafreedom.kdiab.profiles.infrastructure.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.profiles.domain.model.BasalSegment
import org.javafreedom.kdiab.profiles.domain.model.IcrSegment
import org.javafreedom.kdiab.profiles.domain.model.IsfSegment
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus
import org.javafreedom.kdiab.profiles.domain.model.TargetSegment
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

@kotlinx.serialization.Serializable
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
    val userId = uuid("user_id") // No foreign key
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

object Insulins : Table("insulins") {
    private const val NAME_LENGTH = 255

    val id = uuid("id")
    val name = varchar("name", NAME_LENGTH).uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}
