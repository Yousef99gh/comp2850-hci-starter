package routes

import data.TaskRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.pebbletemplates.pebble.PebbleEngine
import java.io.StringWriter

fun Route.taskRoutes() {

    val pebble =
        PebbleEngine
            .Builder()
            .loader(
                io.pebbletemplates.pebble.loader.ClasspathLoader().apply {
                    prefix = "templates/"
                },
            )
            .build()

    fun render(templateName: String, model: Map<String, Any?>): String {
        val template = pebble.getTemplate(templateName)
        val writer = StringWriter()
        template.evaluate(writer, model)
        return writer.toString()
    }

    fun ApplicationCall.isHtmx(): Boolean =
        request.headers["HX-Request"]?.equals("true", ignoreCase = true) == true

    // GET /tasks
    get("/tasks") {
        val editingId = call.request.queryParameters["editingId"]?.toIntOrNull()

        val model =
            mapOf(
                "title" to "Tasks",
                "tasks" to TaskRepository.all(),
                "editingId" to editingId
            )

        call.respondText(render("tasks/index.peb", model), ContentType.Text.Html)
    }

    // POST /tasks (add)
    post("/tasks") {
        val title = call.receiveParameters()["title"].orEmpty().trim()

        if (title.isBlank()) {
            if (call.isHtmx()) {
                val error = """
<div id="taskError" class="error" hx-swap-oob="true" role="alert" aria-live="polite">
  Task name can't be empty. Please type something like "Buy milk".
</div>
""".trimIndent()
                return@post call.respondText(error, ContentType.Text.Html, HttpStatusCode.UnprocessableEntity)
            }

            call.response.headers.append(HttpHeaders.Location, "/tasks")
            return@post call.respond(HttpStatusCode.SeeOther)
        }

        val task = TaskRepository.add(title)

        if (call.isHtmx()) {
            val fragment =
                render(
                    "tasks/_item.peb",
                    mapOf("task" to task)
                )

            val clearError = """
<div id="taskError" class="error" hx-swap-oob="true" hidden></div>
""".trimIndent()

            val toast = """
<div id="toast" class="toast" hx-swap-oob="true" role="status" aria-live="polite">
  Task "${task.title}" added.
</div>
""".trimIndent()

            return@post call.respondText(fragment + clearError + toast, ContentType.Text.Html, HttpStatusCode.Created)
        }

        call.response.headers.append(HttpHeaders.Location, "/tasks")
        call.respond(HttpStatusCode.SeeOther)
    }

    // POST /tasks/{id}/delete
    post("/tasks/{id}/delete") {
        val id = call.parameters["id"]?.toIntOrNull()
        val removed = id?.let { TaskRepository.delete(it) } ?: false

        if (call.isHtmx()) {
            val message = if (removed) "Task deleted." else "Could not delete task."
            val toast = """
<div id="toast" class="toast" hx-swap-oob="true" role="status" aria-live="polite">
  $message
</div>
""".trimIndent()

            // empty body is fine when hx-swap="delete" or outerHTML removal is used in template
            return@post call.respondText(toast, ContentType.Text.Html)
        }

        call.response.headers.append(HttpHeaders.Location, "/tasks")
        call.respond(HttpStatusCode.SeeOther)
    }

    // GET /tasks/{id}/edit (enter edit mode)
    get("/tasks/{id}/edit") {
        val id = call.parameters["id"]?.toIntOrNull()
        val task = id?.let { TaskRepository.find(it) }

        if (task == null) return@get call.respond(HttpStatusCode.NotFound)

        if (call.isHtmx()) {
            val html = render("tasks/_edit.peb", mapOf("task" to task, "error" to null))
            return@get call.respondText(html, ContentType.Text.Html)
        }

        call.response.headers.append(HttpHeaders.Location, "/tasks?editingId=${task.id}")
        call.respond(HttpStatusCode.SeeOther)
    }

    // POST /tasks/{id}/edit (save edit)
    post("/tasks/{id}/edit") {
        val id = call.parameters["id"]?.toIntOrNull()
        val newTitle = call.receiveParameters()["title"].orEmpty().trim()

        if (id == null) return@post call.respond(HttpStatusCode.BadRequest)

        val task = TaskRepository.find(id) ?: return@post call.respond(HttpStatusCode.NotFound)

        if (newTitle.isBlank()) {
            if (call.isHtmx()) {
                val html = render("tasks/_edit.peb", mapOf("task" to task, "error" to "Title can't be empty."))
                return@post call.respondText(html, ContentType.Text.Html, HttpStatusCode.UnprocessableEntity)
            }

            call.response.headers.append(HttpHeaders.Location, "/tasks?editingId=$id")
            return@post call.respond(HttpStatusCode.SeeOther)
        }

        TaskRepository.update(id, newTitle)
        val updated = TaskRepository.find(id) ?: return@post call.respond(HttpStatusCode.NotFound)

        if (call.isHtmx()) {
            val html = render("tasks/_item.peb", mapOf("task" to updated))

            val toast = """
<div id="toast" class="toast" hx-swap-oob="true" role="status" aria-live="polite">
  Task updated.
</div>
""".trimIndent()

            return@post call.respondText(html + toast, ContentType.Text.Html)
        }

        call.response.headers.append(HttpHeaders.Location, "/tasks")
        call.respond(HttpStatusCode.SeeOther)
    }

    // GET /tasks/{id}/view (cancel edit)
    get("/tasks/{id}/view") {
        val id = call.parameters["id"]?.toIntOrNull()
        val task = id?.let { TaskRepository.find(it) }

        if (task == null) return@get call.respond(HttpStatusCode.NotFound)

        if (call.isHtmx()) {
            val html = render("tasks/_item.peb", mapOf("task" to task))
            return@get call.respondText(html, ContentType.Text.Html)
        }

        call.response.headers.append(HttpHeaders.Location, "/tasks")
        call.respond(HttpStatusCode.SeeOther)
    }
}
