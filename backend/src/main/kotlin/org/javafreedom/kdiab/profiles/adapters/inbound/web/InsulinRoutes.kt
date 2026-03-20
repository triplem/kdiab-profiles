@file:Suppress("MatchingDeclarationName")
package org.javafreedom.kdiab.profiles.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.profiles.domain.repository.InsulinRepository
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.api.models.Insulin as ApiInsulin
import org.javafreedom.kdiab.profiles.domain.model.Insulin as DomainInsulin
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
                val newInsulin = repository.create(request.name).toApi()
                call.respond(HttpStatusCode.Created, newInsulin)
            }

            route("/{id}") {
                put {
                    val id = call.parameters["id"]?.let { Uuid.parse(it) } 
                        ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val request = call.receive<InsulinRequest>()
                    val updated = repository.update(id, request.name)?.toApi()
                    if (updated != null) {
                        call.respond(HttpStatusCode.OK, updated)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
                
                delete {
                    val id = call.parameters["id"]?.let { Uuid.parse(it) } 
                        ?: return@delete call.respond(HttpStatusCode.BadRequest)
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
