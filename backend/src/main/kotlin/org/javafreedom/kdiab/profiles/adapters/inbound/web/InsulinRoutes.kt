@file:Suppress("MatchingDeclarationName")
package org.javafreedom.kdiab.profiles.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.profiles.domain.exception.ConflictException
import org.javafreedom.kdiab.profiles.domain.repository.InsulinRepository
import org.javafreedom.kdiab.profiles.plugins.ErrorResponse
import org.javafreedom.kdiab.profiles.plugins.UserPrincipal
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.api.models.Insulin as ApiInsulin
import org.javafreedom.kdiab.profiles.domain.model.Insulin as DomainInsulin

private const val INSULIN_NAME_MAX_LENGTH = 255
private const val DUPLICATE_INSULIN_MSG = "An insulin with that name already exists"
private const val INVALID_NAME_MSG = "Insulin name must be 1–255 characters"

private fun isValidInsulinName(name: String) = name.isNotBlank() && name.length <= INSULIN_NAME_MAX_LENGTH

@Serializable
data class InsulinRequest(val name: String)

fun Route.insulinRoutes(repository: InsulinRepository) {
    authenticate {
        route("/api/v1/insulins") {
            get {
                val insulins = repository.findAll().map { it.toApi() }
                call.respond(HttpStatusCode.OK, insulins)
            }

            post {
                val request = call.receive<InsulinRequest>()
                if (!isValidInsulinName(request.name)) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(HttpStatusCode.BadRequest.value, INVALID_NAME_MSG)
                    )
                    return@post
                }
                try {
                    val newInsulin = repository.create(request.name).toApi()
                    call.respond(HttpStatusCode.Created, newInsulin)
                } catch (e: org.jetbrains.exposed.v1.exceptions.ExposedSQLException) {
                    throw ConflictException(DUPLICATE_INSULIN_MSG, e)
                }
            }

            route("/{id}") {
                put {
                    val principal = call.principal<UserPrincipal>()
                    if (principal == null || !principal.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@put
                    }
                    val idString = call.parameters["id"]
                    if (idString == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@put
                    }
                    val id = Uuid.parse(idString)
                    val request = call.receive<InsulinRequest>()
                    if (!isValidInsulinName(request.name)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(HttpStatusCode.BadRequest.value, INVALID_NAME_MSG)
                        )
                        return@put
                    }
                    val updated = try {
                        repository.update(id, request.name)?.toApi()
                    } catch (e: org.jetbrains.exposed.v1.exceptions.ExposedSQLException) {
                        throw ConflictException(DUPLICATE_INSULIN_MSG, e)
                    }
                    if (updated != null) {
                        call.respond(HttpStatusCode.OK, updated)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete {
                    val principal = call.principal<UserPrincipal>()
                    if (principal == null || !principal.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@delete
                    }
                    val idString = call.parameters["id"]
                    if (idString == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@delete
                    }
                    val id = Uuid.parse(idString)
                    val deleted = repository.delete(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}

private fun DomainInsulin.toApi(): ApiInsulin {
    return ApiInsulin(
        id = this.id.toString(),
        name = this.name
    )
}
