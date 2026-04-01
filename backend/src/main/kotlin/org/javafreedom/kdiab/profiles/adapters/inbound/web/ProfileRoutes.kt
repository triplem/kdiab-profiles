package org.javafreedom.kdiab.profiles.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.api.models.CreateProfileRequest
import org.javafreedom.kdiab.profiles.application.service.ProfileService
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun Route.profileRoutes(profileService: ProfileService) {

    authenticate {
        listProfiles(profileService)
        getProfileHistory(profileService)
        getProfile(profileService)
        createProfile(profileService)
        updateProfile(profileService)
        activateProfile(profileService)
        acceptProposedProfile(profileService)
        rejectProposedProfile(profileService)
        deleteSegment(profileService)
        deleteProfile(profileService)
        deleteAllProfiles(profileService)
    }
}

private fun Route.listProfiles(profileService: ProfileService) {
    get<org.javafreedom.kdiab.profiles.api.Paths.listProfiles> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkReadAccess(principal, targetUserId)

        val profiles = profileService.getProfiles(targetUserId)
        call.respond(profiles.map { it.toApi() })
    }
}

private fun Route.getProfileHistory(profileService: ProfileService) {
    get<org.javafreedom.kdiab.profiles.api.Paths.getProfileHistory> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkReadAccess(principal, targetUserId)

        val from = kotlinx.datetime.Instant.parse(params.from)
        val to = kotlinx.datetime.Instant.parse(params.to)

        val history = profileService.getHistory(targetUserId, from, to)
        call.respond(history.map { it.toApi() })
    }
}

private fun Route.getProfile(profileService: ProfileService) {
    get<org.javafreedom.kdiab.profiles.api.Paths.getProfile> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkReadAccess(principal, targetUserId)

        val profileId = Uuid.parse(params.profileId)
        val profile = profileService.getProfile(profileId)

        if (profile != null) {
            if (profile.userId != targetUserId) {
                throw org.javafreedom.kdiab.profiles.domain.exception.ResourceNotFoundException(
                        "Profile not found"
                )
            }
            call.respond(profile.toApi())
        } else {
            throw org.javafreedom.kdiab.profiles.domain.exception.ResourceNotFoundException(
                    "Profile not found"
            )
        }
    }
}

private fun Route.createProfile(profileService: ProfileService) {
    post<org.javafreedom.kdiab.profiles.api.Paths.createProfile> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkReadAccess(principal, targetUserId)

        val status = if (principal?.isDoctor() == true && principal.userId != targetUserId) {
            org.javafreedom.kdiab.profiles.domain.model.ProfileStatus.PROPOSED
        } else {
            checkWriteAccess(principal, targetUserId)
            org.javafreedom.kdiab.profiles.domain.model.ProfileStatus.DRAFT
        }

        val request = call.receive<CreateProfileRequest>()
        val domainProfile = request.toDomain(targetUserId, status)
        val created = profileService.createProfile(domainProfile)
        logger.info { "Created profile ${created.id} for user $targetUserId with status $status" }
        call.respond(HttpStatusCode.Created, created.toApi())
    }
}

private fun Route.updateProfile(profileService: ProfileService) {
    put<org.javafreedom.kdiab.profiles.api.Paths.updateProfile> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkWriteAccess(principal, targetUserId)

        val profileId = Uuid.parse(params.profileId)
        val request = call.receive<org.javafreedom.kdiab.profiles.api.models.Profile>()
        val domainProfile = request.toDomain()

        // Ensure the ID matches the path
        if (domainProfile.id != profileId) {
            throw org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException(
                    "Profile ID mismatch"
            )
        }

        val updated = profileService.updateProfile(domainProfile)
        logger.info { "Updated profile ${domainProfile.id} for user $targetUserId" }
        call.respond(updated.toApi())
    }
}

private fun Route.activateProfile(profileService: ProfileService) {
    post<org.javafreedom.kdiab.profiles.api.Paths.activateProfile> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkWriteAccess(principal, targetUserId)

        val profileId = Uuid.parse(params.profileId)
        val activated = profileService.activateProfile(targetUserId, profileId)
        logger.info { "Activated profile $profileId for user $targetUserId" }
        call.respond(activated.toApi())
    }
}

private fun Route.acceptProposedProfile(profileService: ProfileService) {
    post<org.javafreedom.kdiab.profiles.api.Paths.acceptProposedProfile> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkWriteAccess(principal, targetUserId)

        val profileId = Uuid.parse(params.profileId)
        val activated = profileService.acceptProposedProfile(targetUserId, profileId)
        call.respond(activated.toApi())
    }
}

private fun Route.rejectProposedProfile(profileService: ProfileService) {
    post<org.javafreedom.kdiab.profiles.api.Paths.rejectProposedProfile> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkWriteAccess(principal, targetUserId)

        val profileId = Uuid.parse(params.profileId)
        val rejected = profileService.rejectProposedProfile(targetUserId, profileId)
        call.respond(rejected.toApi())
    }
}

private fun Route.deleteSegment(profileService: ProfileService) {
    delete<org.javafreedom.kdiab.profiles.api.Paths.deleteSegment> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkWriteAccess(principal, targetUserId)

        val profileId = Uuid.parse(params.profileId)
        val segmentType = params.segmentType
        val startTime = kotlinx.datetime.LocalTime.parse(params.startTime)

        val updated = profileService.deleteSegment(targetUserId, profileId, segmentType, startTime)
        call.respond(updated.toApi())
    }
}

private fun Route.deleteProfile(profileService: ProfileService) {
    delete<org.javafreedom.kdiab.profiles.api.Paths.deleteProfile> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkWriteAccess(principal, targetUserId)

        val profileId = Uuid.parse(params.profileId)
        val deleted = profileService.deleteProfile(targetUserId, profileId)
        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            throw org.javafreedom.kdiab.profiles.domain.exception.ResourceNotFoundException(
                    "Profile not found"
            )
        }
    }
}

private fun Route.deleteAllProfiles(profileService: ProfileService) {
    delete<org.javafreedom.kdiab.profiles.api.Paths.deleteProfiles> { params ->
        val principal = call.principal<org.javafreedom.kdiab.profiles.plugins.UserPrincipal>()
        val targetUserId = Uuid.parse(params.userId)

        checkWriteAccess(principal, targetUserId)

        val deleted = profileService.deleteAllProfiles(targetUserId)
        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            throw org.javafreedom.kdiab.profiles.domain.exception.ResourceNotFoundException(
                    "Profiles not found"
            )
        }
    }
}

private fun checkReadAccess(
        principal: org.javafreedom.kdiab.profiles.plugins.UserPrincipal?,
        targetUserId: Uuid
) {
    if (principal == null || !principal.canAccess(targetUserId)) {
        throw org.javafreedom.kdiab.profiles.domain.exception.AuthorizationException(
                "Access Not Authorized"
        )
    }
}

private fun checkWriteAccess(
        principal: org.javafreedom.kdiab.profiles.plugins.UserPrincipal?,
        targetUserId: Uuid
) {
    if (principal == null || (principal.userId != targetUserId && !principal.isAdmin())) {
        throw org.javafreedom.kdiab.profiles.domain.exception.AuthorizationException(
                "Write Access Denied"
        )
    }
}
