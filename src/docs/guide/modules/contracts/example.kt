package guide.modules.contracts

import org.http4k.contract.ApiInfo
import org.http4k.contract.ApiKey
import org.http4k.contract.ContractRoute
import org.http4k.contract.OpenApi
import org.http4k.contract.bind
import org.http4k.contract.contract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.TEXT_PLAIN
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with

// for this example we're using Jackson - note that the auto method imported is an extension
// function that is defined on the Jackson instance
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto

import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.routing.routes

// this route has a dynamic path segment
fun greetRoute(): ContractRoute {

    // these lenses define the dynamic parts of the request that will be used in processing
    val ageQuery = Query.int().required("age")
    val stringBody = Body.string(TEXT_PLAIN).toLens()

    // this specifies the route contract, with the desired contract of path, headers, queries and body parameters.
    val spec = "/greet" / Path.of("name") meta {
        summary = "tells the user hello!"
        queries += ageQuery
        receiving(stringBody)
    } bindContract GET

    // the this function will dynamically supply a new HttpHandler for each call. The number of parameters
    // matches the number of dynamic sections in the path (1)
    fun greet(nameFromPath: String): HttpHandler = { request: Request ->
        val age = ageQuery(request)
        val sentMessage = stringBody(request)

        Response(OK).with(stringBody of "hello $nameFromPath you are $age. You sent $sentMessage")
    }

    return spec to ::greet
}

// this route uses auto-marshalling to convert the JSON body directly to/from a data class instance
fun echoRoute(): ContractRoute {

    data class NameAndMessage(val name: String, val message: String)

    // the body lens here is imported as an extension function from the Jacskson instance
    val body = Body.auto<NameAndMessage>().toLens()

    // this specifies the route contract, including examples of the input and output body objects - they will
    // get exploded into JSON schema in the OpenAPI docs
    val spec = "/echo" meta {
        summary = "echoes the name and message sent to it"
        receiving(body to NameAndMessage("jim", "hello!"))
        returning(OK, body to NameAndMessage("jim", "hello!"))
    } bindContract POST

    // note that because we don't have any dynamic parameters, we can use a HttpHandler instance instad of a function
    val echo: HttpHandler = { request: Request ->
        val received: NameAndMessage = body(request)
        Response(OK).with(body of received)
    }

    return spec to echo
}

// use another Lens to set up the API-key - the answer is 42!
val security = ApiKey(Query.int().required("api"), { it == 42 })

// Combine the Routes into a contract and bind to a context, defining a renderer (in this example
// OpenApi/Swagger) and a security model (in this case an API-Key):
val contract = contract(OpenApi(ApiInfo("My great API", "v1.0"), Jackson), "/swagger.json", security,
    greetRoute(),
    echoRoute()
)
val handler: HttpHandler = routes("/api/v1" bind contract)


// by default, the OpenAPI docs live at the root of the contract context, but we can override it..
fun main() {
    println(handler(Request(GET, "/api/v1/swagger.json")))
}
