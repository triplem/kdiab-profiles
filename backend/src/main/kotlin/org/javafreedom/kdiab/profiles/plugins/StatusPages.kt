package org.javafreedom.kdiab.profiles.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.javafreedom.kdiab.profiles.domain.exception.AuthenticationException
import org.javafreedom.kdiab.profiles.domain.exception.AuthorizationException
import org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.profiles.domain.exception.ResourceNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<AuthenticationException> { call, cause ->
            logger.warn(cause) { "Authentication failure" }
            call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                            HttpStatusCode.Unauthorized.value,
                            cause.message ?: "Unauthorized"
                    )
            )
        }
        exception<AuthorizationException> { call, cause ->
            logger.warn(cause) { "Authorization failure" }
            call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse(HttpStatusCode.Forbidden.value, cause.message ?: "Forbidden")
            )
        }
        exception<ResourceNotFoundException> { call, cause ->
            logger.debug(cause) { "Resource not found" }
            call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(HttpStatusCode.NotFound.value, cause.message ?: "Not Found")
            )
        }
        exception<BusinessValidationException> { call, cause ->
            logger.warn(cause) { "Business validation failure" }
            call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(HttpStatusCode.BadRequest.value, cause.message ?: "Bad Request")
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            logger.warn(cause) { "Illegal argument" }
            call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                            HttpStatusCode.BadRequest.value,
                            cause.message ?: "Invalid Argument"
                    )
            )
        }
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled internal server error" }
            call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(HttpStatusCode.InternalServerError.value, "Internal Server Error")
            )
        }
    }
}
