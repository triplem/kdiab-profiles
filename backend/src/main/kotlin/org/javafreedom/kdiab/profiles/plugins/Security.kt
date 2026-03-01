package org.javafreedom.kdiab.profiles.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.domain.model.Role

fun Application.configureSecurity() {
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtDomain = environment.config.property("jwt.domain").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()
    val jwtSecret = environment.config.property("jwt.secret").getString()

    authentication {
        jwt {
            realm = jwtRealm
            verifier(
                    JWT.require(Algorithm.HMAC256(jwtSecret))
                            .withAudience(jwtAudience)
                            .withIssuer(jwtDomain)
                            .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) {
                    val userId = Uuid.parse(credential.payload.subject)
                    val rawRoles =
                            credential.payload.getClaim("roles").asList(String::class.java)
                                    ?: emptyList()
                    val roles = rawRoles.mapNotNull { Role.fromString(it) }.toSet()

                    val allowedPatients =
                            credential
                                    .payload
                                    .getClaim("allowed_patients")
                                    .asList(String::class.java)
                                    ?.map { Uuid.parse(it) }
                                    ?.toSet()
                                    ?: emptySet()

                    UserPrincipal(userId, roles, allowedPatients)
                } else {
                    null
                }
            }
        }
    }
}

data class UserPrincipal(val userId: Uuid, val roles: Set<Role>, val allowedPatients: Set<Uuid>) {
    fun isAdmin() = roles.contains(Role.ADMIN)
    fun isDoctor() = roles.contains(Role.DOCTOR)
    fun canAccess(targetUserId: Uuid) =
            userId == targetUserId ||
                    isAdmin() ||
                    (isDoctor() && allowedPatients.contains(targetUserId))
}
