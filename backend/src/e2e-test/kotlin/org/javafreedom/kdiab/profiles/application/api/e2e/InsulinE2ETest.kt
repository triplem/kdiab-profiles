package org.javafreedom.kdiab.profiles.adapters.inbound.web.e2e

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.*
import java.util.Date
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.adapters.inbound.web.InsulinRequest
import org.javafreedom.kdiab.profiles.api.models.Insulin as ApiInsulin
import org.javafreedom.kdiab.profiles.module

class InsulinE2ETest :
    BehaviorSpec({
        val jwtDomain = "https://jwt-provider-domain/"
        val jwtAudience = "jwt-audience"
        val jwtRealm = "kdiab-profiles"
        val jwtSecret = "secret"

        fun generateToken(
            userId: Uuid,
            roles: List<String> = listOf("USER")
        ): String {
            return JWT.create()
                .withAudience(jwtAudience)
                .withIssuer(jwtDomain)
                .withSubject(userId.toString())
                .withClaim("roles", roles)
                .withExpiresAt(Date(System.currentTimeMillis() + 60000))
                .sign(Algorithm.HMAC256(jwtSecret))
        }

        given("A running Profile Service for Insulins") {
            `when`("I fetch and create insulins") {
                then("It should return an empty list initially, create a new one, and then return it") {
                    testApplication {
                        environment {
                            config = MapApplicationConfig(
                                "jwt.audience" to jwtAudience,
                                "jwt.domain" to jwtDomain,
                                "jwt.realm" to jwtRealm,
                                "jwt.secret" to jwtSecret,
                                "jwt.test" to "true",
                                "storage.driverClassName" to "org.h2.Driver",
                                "storage.jdbcUrl" to "jdbc:h2:mem:e2e_insulins;DB_CLOSE_DELAY=-1",
                                "storage.username" to "root",
                                "storage.password" to "password",
                                "storage.maximumPoolSize" to "3",
                                "storage.isAutoCommit" to "false",
                                "storage.transactionIsolation" to "TRANSACTION_REPEATABLE_READ"
                            )
                        }
                        application { module() }
                        val client = createClient {
                            install(ContentNegotiation) { json() }
                        }

                        val userId = Uuid.random()
                        val token = generateToken(userId)

                        // 1. Fetch all - should have 3 defaults initially
                        val getResponse = client.get("/api/v1/insulins") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }
                        getResponse.status shouldBe HttpStatusCode.OK
                        val emptyInsulins = getResponse.bodyAsText().let {
                            kotlinx.serialization.json.Json.decodeFromString<List<ApiInsulin>>(it)
                        }
                        emptyInsulins.shouldHaveSize(3)

                        // 2. Create Insulin
                        val createRequest = InsulinRequest(name = "NovoRapid")
                        val createResponse = client.post("/api/v1/insulins") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                            contentType(ContentType.Application.Json)
                            setBody(createRequest)
                        }
                        println("CREATE RESPONSE STATUS: ${createResponse.status}")
                        println("CREATE RESPONSE BODY: ${createResponse.bodyAsText()}")
                        createResponse.status shouldBe HttpStatusCode.Created
                        val createdInsulin = createResponse.bodyAsText().let {
                            kotlinx.serialization.json.Json.decodeFromString<ApiInsulin>(it)
                        }
                        createdInsulin.id.shouldNotBeBlank()
                        createdInsulin.name shouldBe "NovoRapid"

                        // 3. Fetch all again - should have 4 items
                        val getResponse2 = client.get("/api/v1/insulins") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }
                        getResponse2.status shouldBe HttpStatusCode.OK
                        val insulinList = getResponse2.bodyAsText().let {
                            kotlinx.serialization.json.Json.decodeFromString<List<ApiInsulin>>(it)
                        }
                        insulinList.shouldHaveSize(4)
                        insulinList.map { it.name }.contains("NovoRapid") shouldBe true
                    }
                }
            }
        }
    })
