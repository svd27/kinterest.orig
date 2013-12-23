package ch.passenger.kinterest.jetty

import ch.passenger.kinterest.service.KIApplication
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import javax.servlet.Servlet
import java.util.HashMap
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.servlet.Holder
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import ch.passenger.kinterest.service.KISession
import ch.passenger.kinterest.service.KIPrincipal
import ch.passenger.kinterest.service.KIService
import com.fasterxml.jackson.databind.ObjectMapper
import javax.servlet.ServletException
import ch.passenger.kinterest.service.InterestService
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import javax.servlet.http.HttpSession
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonFactory
import ch.passenger.kinterest.service.EventPublisher
import ch.passenger.kinterest.Event
import java.util.regex.Pattern
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.JsonNode
import org.eclipse.jetty.websocket.api.Session
import java.io.Reader
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InputStream

/**
 * Created by svd on 21/12/2013.
 */

//private val log = LoggerFactory.getLogger(javaClass<ApplicationServlet>().getPackage()!!.getName())!!

class ApplicationServlet(val serverContext: ServletContextHandler, val app: KIApplication) {
    {
        log.info("APP")
        serverContext.servlets {
            val res: MutableMap<String, ServletHolder> = HashMap()
            val appServlet = AppServlet(app)
            appServlet.init(this)

            res
        }

        /*
        serverContext.asocket {
            "${app.name}/events" to {(it:HttpSession)->EventSocket(it)}
            "${app.name}/entities" to {(it:HttpSession)->EntitySocket(it)}
        }*/
    }
}

class EventSocket(val http: HttpSession) : KIWebsocketAdapter(http), EventPublisher {
    val kisession: KISession get() = http!!.getAttribute(KIServlet.SESSION_KEY) as KISession
    override fun onWebSocketText(message: String?) {
        log.info(message)
        //val om = ObjectMapper()!!
        //val json = om.readTree(message)!!
        //val action = json.path("action")?.textValue()
        //val target = json.path("target")?.textValue()
    }


    override fun onWebSocketConnect(sess: Session?) {
        super<KIWebsocketAdapter>.onWebSocketConnect(sess)
        log.info("WS CONNECT: $sess")
        kisession.events = this@EventSocket
    }
    override fun publish(events: Iterable<Event<out Hashable>>) {
        val om = ObjectMapper()
        val ja: ArrayNode = om.createArrayNode()!!
        events.forEach {
            val an: ArrayNode = ja
            an.add(om.valueToTree<JsonNode?>(it))
        }
        send(ja.toString()!!)
    }
}


class EntitySocket(val http: HttpSession) : KIWebsocketAdapter(http) {
    val kisession: KISession get() = http!!.getAttribute(KIServlet.SESSION_KEY) as KISession
    override fun onWebSocketText(message: String?) {
        log.info(message)
    }

}


class AppServlet(app: KIApplication) : KIServlet(app) {
    fun init(ctx: ServletContextHandler) {
        ctx.addServlet(ServletHolder(this), "/${app.name}")
        app.descriptors.forEach {
            log.info("service .... $it")
            val s = it.create()
            if (s is InterestService<*, *>) {
                val path = "/${app.name}/${s.galaxy.descriptor.entity}/*"
                log.info(">>>>>>>>>>>>>>>>ADDING: ${s.galaxy.descriptor.entity} on $path")
                ctx + (path to (ServletHolder(InterestServlet(s, app))))
            }
            ctx.socket {
                "/${app.name}/events" to javaClass<EventSocket>() as Class<KIWebsocketAdapter>
            }

            ctx.socket {

                "/${app.name}/entities" to javaClass<EntitySocket>() as Class<KIWebsocketAdapter>
            }
        }
    }

    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        if (req == null) throw IllegalStateException()
        if (resp == null) throw IllegalStateException()
        resp.setContentType("application/json")
        val on = om.createObjectNode()!!
        on.put("application", app.name)
        on.put("session", KISession.current()?.id)
        on.put("principal", KISession.current()?.principal?.principal)
        resp.getWriter()?.write(on.toString()!!)
        resp.getWriter()?.flush()
        resp.getWriter()?.close()
    }
}

class InterestServlet(val service: InterestService<*, *>, app: KIApplication) : KIServlet(app) {
    val patCrt = Pattern.compile("/create/([A-Za-z]+)")
    val patFilter = Pattern.compile("/filter/([0-9]+)")

    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        if (req == null) throw IllegalStateException()
        if (resp == null) throw IllegalStateException()
        validate(req)

        resp.setHeader("Cache-Control", "no-cache")
        resp.setHeader("Access-Control-Allow-Origin", "*")

        val path = req.getPathInfo()!!
        val mCrt = patCrt.matcher(path)
        if (mCrt.matches()) {
            val i = service.create(mCrt.group(1)!!)
            val json = om.createObjectNode()!!
            json.put("response", "ok")
            json.put("interest", i)
            resp.setContentType("application/json")
            resp.setContentLength(json.toString()!!.getBytes("UTF-8").size)
            log.info("writing $json")
            //resp.getWriter()?.write()
            resp.getWriter()?.print(json.toString()!!)
            resp.flushBuffer()

        } else throw IllegalArgumentException("unknown GET: $path")
    }


    fun Reader.eachLine(cb: (String) -> Unit) {
        var l = readLine()
        while (l != null) {
            cb(l!!)
            l = readLine()
        }
    }

    override fun doPost(req: HttpServletRequest?, resp: HttpServletResponse?) {
        if (req == null) throw IllegalStateException()
        if (resp == null) throw IllegalStateException()
        validate(req)

        resp.setHeader("Cache-Control", "no-cache")
        resp.setHeader("Access-Control-Allow-Origin", "*")

        val path = req.getPathInfo()!!
        val mf = patFilter.matcher(path)
        if (mf.matches()) {
            val sint = mf.group(1)!!
            val id = Integer.parseInt(sint)

            val ips = req.getInputStream()!!
            val f = read(ips)
            log.info("FILTER: $f")

            val on = om.readTree(f)
            service.filter(id, on as ObjectNode)
            resp.getWriter()?.print("{response: 'ok'}")
            resp.flushBuffer()
        } else throw IllegalArgumentException("unknown POST: $path")
    }


    fun validate(req: HttpServletRequest) {
        log.info("PATH: ${req.getPathInfo()}")
    }

    fun read(ips:InputStream) :String {
        val scanner = java.util.Scanner(ips, "UTF-8").useDelimiter("\\A")
        return if(scanner.hasNext()) scanner.next() else ""
    }
}

abstract class KIServlet(val app: KIApplication) : HttpServlet() {
    protected val om: ObjectMapper = ObjectMapper()
    protected open val log: Logger = LoggerFactory.getLogger(this.javaClass)!!
    private val currentSession: ThreadLocal<KISession> = ThreadLocal()
    private val currentApp: ThreadLocal<KIApplication> = ThreadLocal()
    override fun service(req: HttpServletRequest?, resp: HttpServletResponse?) {
        if (req == null) throw IllegalStateException()
        if (resp == null) throw IllegalStateException()
        val hnen = req.getHeaderNames()!!
        while (hnen.hasMoreElements()) {
            val hname = hnen.nextElement()!!
            req.getHeaders(hname)?.iterator()?.forEach {
                log.info("HEADER: $hname = '$it'")
            }
        }
        val httpSession = req.getSession(true)!!
        if (httpSession.getAttribute(SESSION_KEY) == null) {
            httpSession.setAttribute(SESSION_KEY, KISession(KIPrincipal.ANONYMOUS, app))
        }
        if (httpSession.getAttribute(APPLICATION_KEY) == null) {
            httpSession.setAttribute(APPLICATION_KEY, app)
        }
        val s = httpSession.getAttribute(SESSION_KEY)!! as KISession
        currentSession.set(s)
        currentApp.set(app)
        s.current()
        super.service(req, resp)
    }

    protected fun app(s: HttpSession): KIApplication? = s.getAttribute(APPLICATION_KEY) as KIApplication


    class object {
        val SESSION_KEY = "KISESSION"
        val APPLICATION_KEY = "KIAPP"
    }
}