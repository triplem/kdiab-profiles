package org.javafreedom.kdiab.profiles

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.profiles.adapters.inbound.web.profileRoutes
import org.javafreedom.kdiab.profiles.adapters.inbound.web.insulinRoutes
import org.javafreedom.kdiab.profiles.application.service.ProfileService
import org.javafreedom.kdiab.profiles.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedProfileRepository
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedInsulinRepository
import org.javafreedom.kdiab.profiles.plugins.configureSecurity
import org.javafreedom.kdiab.profiles.plugins.configureStatusPages
import org.javafreedom.kdiab.profiles.plugins.configureLogging
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module(
        profileService: ProfileService = ProfileService(ExposedProfileRepository()),
        insulinRepository: org.javafreedom.kdiab.profiles.domain.repository.InsulinRepository =
                ExposedInsulinRepository(),
        initDatabase: Boolean = true
) {
    configureLogging()
    configureSecurity()
    configureStatusPages()
    install(ContentNegotiation) {
        json(
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
        )
    }
    install(Resources)

    // Initialize Database if requested
    if (initDatabase) {
        DatabaseFactory.init(environment.config)
    }

    routing {
        get("/") { call.respondText("T1D Profile Service is running!") }
        get("/healthz") { call.respond(io.ktor.http.HttpStatusCode.OK) }

        route("/api/v1") {
            profileRoutes(profileService)
        }
        insulinRoutes(insulinRepository)

        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
    }
}
