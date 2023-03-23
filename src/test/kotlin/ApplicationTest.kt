package org.example

import com.hexagonkt.core.logging.info
import com.hexagonkt.core.media.APPLICATION_JSON
import com.hexagonkt.http.client.HttpClient
import com.hexagonkt.http.client.jetty.JettyClientAdapter
import com.hexagonkt.http.model.HttpMethod.POST
import com.hexagonkt.http.model.NOT_FOUND_404
import com.hexagonkt.http.model.OK_200
import com.hexagonkt.http.server.model.HttpServerRequest
import com.hexagonkt.serialization.jackson.json.Json
import com.hexagonkt.serialization.parseMap
import com.hexagonkt.serialization.serialize
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.net.URL
import java.time.LocalDateTime
import kotlin.test.assertEquals

@TestInstance(PER_CLASS)
internal class ApplicationTest {

    private val client by lazy {
        HttpClient(JettyClientAdapter(), URL("http://localhost:${restApi.server.runtimePort}"))
    }

    @BeforeAll fun beforeAll() {
        main()
        client.start()
    }

    @AfterAll fun afterAll() {
        client.stop()
        restApi.server.stop()
    }

    @Test fun `HTTP router returns proper status, headers and body`() {
        val now = LocalDateTime.now()
        val appointment = Appointment("1", listOf("mike", "jill"), now, now.plusHours(1))
        val appointmentMessage = AppointmentMessage(appointment)
        val body = appointmentMessage.data.serialize(Json)
        val handler = restApi.applicationHandler

        handler.process(HttpServerRequest(POST, path="/api/appointments", body = body)).apply {
            assertEquals(OK_200, status)
            assertEquals(APPLICATION_JSON, response.contentType?.mediaType)
        }
    }

    @Test fun `HTTP request returns proper status, headers and body`() {
        val now = LocalDateTime.now()
        val appointment = Appointment("1", listOf("mike", "jill"), now, now.plusHours(1))
        val appointmentMessage = AppointmentMessage(appointment)
        val body = appointmentMessage.data.serialize(Json)

        client.post("/api/appointments", body).apply {
            bodyString().info()
            assertEquals(OK_200, status)
            assertEquals(APPLICATION_JSON, contentType?.mediaType)
        }

        client.get("/api/appointments/1").apply {
            bodyString().info()
            assertEquals("1", bodyString().parseMap(Json)["id"])
            assertEquals(OK_200, status)
            assertEquals(APPLICATION_JSON, contentType?.mediaType)
        }

        client.delete("/api/appointments/1").apply {
            bodyString().info()
            assertEquals("1", bodyString().parseMap(Json)["id"])
            assertEquals(OK_200, status)
            assertEquals(APPLICATION_JSON, contentType?.mediaType)
        }

        client.get("/api/appointments/1").apply {
            bodyString().info()
            assertEquals(NOT_FOUND_404, status)
            assertEquals(APPLICATION_JSON, contentType?.mediaType)
        }

        client.delete("/api/appointments/1").apply {
            bodyString().info()
            assertEquals(NOT_FOUND_404, status)
            assertEquals(APPLICATION_JSON, contentType?.mediaType)
        }
    }
}
