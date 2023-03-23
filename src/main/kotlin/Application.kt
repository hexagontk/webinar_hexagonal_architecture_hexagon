@file:Suppress("MemberVisibilityCanBePrivate")

package org.example

import com.hexagonkt.core.ALL_INTERFACES
import com.hexagonkt.core.fieldsMapOf
import com.hexagonkt.core.logging.Logger
import com.hexagonkt.core.media.APPLICATION_JSON
import com.hexagonkt.http.server.*
import com.hexagonkt.http.server.jetty.JettyServletAdapter
import com.hexagonkt.core.require
import com.hexagonkt.http.model.ContentType
import com.hexagonkt.http.server.handlers.HttpHandler
import com.hexagonkt.http.server.handlers.path
import com.hexagonkt.serialization.jackson.json.Json
import com.hexagonkt.serialization.parseMap
import com.hexagonkt.serialization.serialize
import java.lang.Exception
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

// model

data class Appointment(
    val id: String,
    val userIds: List<String>,
    val start: LocalDateTime,
    val end: LocalDateTime,
)

// domain / service / use cases

class AppointmentsService(
    val store: AppointmentsStorePort,
    val notifier: AppointmentsNotifierPort,
) {
    fun create(appointment: Appointment) {
        store.insert(appointment)
        notifier.notify(appointment.userIds, "Make room for an appointment at ${appointment.start}")
    }

    fun delete(id: String): Boolean {
        val appointment = store.get(id) ?: return false
        store.delete(id)
        notifier.notify(appointment.userIds, "You are free at ${appointment.start}")
        return true
    }

    fun get(id: String): Appointment? =
        store.get(id)
}

interface AppointmentsNotifierPort {
    fun notify(userIds: Collection<String>, message: String)
}

interface AppointmentsStorePort {
    fun insert(appointment: Appointment)
    fun delete(id: String)
    fun get(id: String): Appointment?
}

// application / adapters / driven ports

class LoggingAppointmentsNotifier : AppointmentsNotifierPort {
    private val logger = Logger(this::class)

    override fun notify(userIds: Collection<String>, message: String) {
        userIds.forEach {
            logger.info { "$it $message"}
        }
    }
}

class MapAppointmentsStore : AppointmentsStorePort {
    private val map: ConcurrentMap<String, Appointment> = ConcurrentHashMap()

    override fun insert(appointment: Appointment) {
        map[appointment.id] = appointment
    }

    override fun delete(id: String) {
        map.remove(id)
    }

    override fun get(id: String): Appointment? =
        map[id]
}

// rest api / driver adapter / Other drivers (like a CLI) may be created
interface Message {
    val data: Map<String, *>
}

data class AppointmentMessage(override val data: Map<String, *>) : Message {
    val id: String by data
    val userIds: List<String> by data
    val start: String by data
    val end: String by data

    constructor(appointment: Appointment) : this(
        fieldsMapOf(
            AppointmentMessage::id to appointment.id,
            AppointmentMessage::userIds to appointment.userIds,
            AppointmentMessage::start to appointment.start.toString(),
            AppointmentMessage::end to appointment.end.toString(),
        )
    )

    fun appointment(): Appointment =
        Appointment(id, userIds, LocalDateTime.parse(start), LocalDateTime.parse(end))
}

class RestApi(
    private val appointmentsService: AppointmentsService,
) {
    val appointmentsHandler: HttpHandler = path("/appointments") {
        post {
            val data = request.bodyString().parseMap(Json).mapKeys { it.key.toString() }
            val user = AppointmentMessage(data)
            appointmentsService.create(user.appointment())
            ok(user)
        }

        get("/{id}") {
            val id = pathParameters.require("id")
            val appointment = appointmentsService.get(id)
            if (appointment == null)
                notFound(mapOf("id" to id).serialize(Json))
            else
                ok(AppointmentMessage(appointment))
        }

        delete("/{id}") {
            val id = pathParameters.require("id")
            if (appointmentsService.delete(id))
                ok(mapOf("id" to id))
            else
                notFound(mapOf("id" to id))
        }
    }

    val applicationHandler: HttpHandler = path("/api") {
        exception<Exception> { internalServerError(it.message + " BOOM!") }
        after("*") { send(contentType = ContentType(APPLICATION_JSON)) }
        after("*") {
            send(
                body = when (val b = response.body) {
                    is Message -> b.data.serialize(Json)
                    is Map<*, *> -> b.serialize(Json)
                    is String -> b
                    else -> error("")
                }
            )
        }

        use(appointmentsHandler)
    }

    val settings = HttpServerSettings(ALL_INTERFACES, 9090)
    val serverAdapter = JettyServletAdapter(minThreads = 4)
    val server: HttpServer = HttpServer(serverAdapter, applicationHandler, settings)
}

// main

lateinit var restApi: RestApi

fun main() {
    restApi = RestApi(
        AppointmentsService(
            MapAppointmentsStore(),
            LoggingAppointmentsNotifier(),
        )
    )

    restApi.server.start()
}
