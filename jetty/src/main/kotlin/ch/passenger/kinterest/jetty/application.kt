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
import ch.passenger.kinterest.service.EntityPublisher
import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.util.json.Jsonifier
import ch.passenger.kinterest.Universe
import javax.persistence.Entity
import ch.passenger.kinterest.unique
import java.io.File
import java.nio.file.Files
import java.io.FileInputStream
import java.io.ByteArrayInputStream
import ch.passenger.kinterest.label
import java.util.ArrayList
import ch.passenger.kinterest.ElementFilter


/**
 * Created by svd on 21/12/2013.
 */

//private val log = LoggerFactory.getLogger(javaClass<ApplicationServlet>().getPackage()!!.getName())!!

class ApplicationServlet(val serverContext: ServletContextHandler, val app: KIApplication, val rootPath:String) {
    {
        log.info("APP")
        serverContext.servlets {
            val res: MutableMap<String, ServletHolder> = HashMap()
            val appServlet = AppServlet(app, rootPath)
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
        log.debug(message)
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
    override fun publish(events: Iterable<Event<Comparable<Any>>>) {
        val om = ObjectMapper()
        val ja: ArrayNode = om.createArrayNode()!!
        events.forEach {
            val an: ArrayNode = ja
            an.add(Jsonifier.jsonify<Comparable<Any>>(it as Event<Comparable<Any>>))
        }
        send(ja.toString()!!)
    }
}


class EntitySocket(val http: HttpSession) : KIWebsocketAdapter(http), EntityPublisher {
    val kisession: KISession get() = http!!.getAttribute(KIServlet.SESSION_KEY) as KISession
    val om = ObjectMapper()
    override fun onWebSocketText(message: String?) {
        log.debug(message)
    }


    override fun onWebSocketConnect(sess: Session?) {
        super<KIWebsocketAdapter>.onWebSocketConnect(sess)
        log.info("ENTITIES CONNECT: $sess")
        kisession.entities = this
    }

    override fun publish(entities: Iterable<LivingElement<Comparable<Any>>>) {
        val ja = om.createArrayNode()!!

        entities.map {
            Jsonifier.jsonify(
                    it as LivingElement<Comparable<Any>>,
                    it.descriptor(),
                    it.descriptor().properties)
        }.filterNotNull().
        forEach { ja.add(it) }

        //val jsonNode = om.valueToTree<JsonNode>(entities)
        log.info("publish $ja")
        getSession()?.getRemote()?.sendStringByFuture(ja!!.toString())
    }
}


class AppServlet(app: KIApplication, val rootPath:String) : KIServlet(app) {
    fun init(ctx: ServletContextHandler) {
        ctx.addServlet(ServletHolder(this), "/${app.name}")
        ctx.addServlet(ServletHolder(DumperServlet(app)), "/${app.name}/dump")
        ctx.addServlet(ServletHolder(StaticServlet(File(rootPath))), "/${app.name}/static/*")
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
        val starmap = om.createArrayNode()!!
        Universe.starmap().forEach {
            val entity = om.createObjectNode()!!
            entity.put("entity", it.entity)
            val pa = om.createArrayNode()!!
            val dd = it
            it.properties.forEach {
                p -> val pd = it.descriptors[p]!!
                val pn = om.createObjectNode()!!
                pn.put("property", p)
                pn.put("relation", pd.relation)
                pn.put("oneToMany", pd.oneToMany)
                if(pd.relation || pd.oneToMany) {
                    val ann = pd.classOf.getAnnotation(javaClass<Entity>())
                    pn.put("entity", ann?.name())
                } else {
                    pn.put("type", pd.getter.getReturnType()?.getName())
                }

                pn.put("nullable", dd.nullable(p))
                pn.put("unique", pd.getter.unique())
                pn.put("readonly", pd.setter==null)
                pn.put("enum", pd.enum)
                if(pd.enum) {
                    pn.put("enumvalues", om.valueToTree<JsonNode>(pd.enumValues()))
                }
                pn.put("label", pd.getter.label())
                pa.add(pn)
            }
            entity.put("properties", pa)
            starmap.add(entity)
        }
        on.put("starmap", starmap)
        resp.getWriter()?.write(on.toString()!!)
        resp.getWriter()?.flush()
        resp.getWriter()?.close()
    }
}

class InterestServlet(val service: InterestService<*, *>, app: KIApplication) : KIServlet(app) {
    val patCrt = Pattern.compile("/create/([A-Za-z]+)")
    val patFilter = Pattern.compile("/filter/([0-9]+)")
    val patRemove = Pattern.compile("/remove/([0-9]+)")
    val patBuffer = Pattern.compile("/([0-9]+)/offset/([0-9]+)/limit/([0-9]+)")
    val patOrder = Pattern.compile("/([0-9]+)/orderBy")
    val patSave = Pattern.compile("/save")
    val patEntityDelete = Pattern.compile("/delete/([0-9]+)")
    val patCreateEntity = Pattern.compile("/createEntity")
    val patEntityAdd = Pattern.compile("/([0-9]+)/add/([0-9]+)")
    val patEntityRemove = Pattern.compile("/([0-9]+)/remove/([0-9]+)")
    val patClear  = Pattern.compile("/([0-9]+)/clear")
    val patRelAdd  = Pattern.compile("/entity/([0-9]+)/([A-Za-z]+)/add/([0-9]+)")
    val patRelRem  = Pattern.compile("/entity/([0-9]+)/([A-Za-z]+)/remove/([0-9]+)")
    val patRefresh  = Pattern.compile("/([0-9]+)/refresh")
    val patAction = Pattern.compile("/entity/([0-9]+)/action/([A-Za-z]+)")


    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        if (req == null) throw IllegalStateException()
        if (resp == null) throw IllegalStateException()
        validate(req)

        resp.setHeader("Cache-Control", "no-cache")
        resp.setHeader("Access-Control-Allow-Origin", "*")

        val path = req.getPathInfo()!!
        val mCrt = patCrt.matcher(path)
        val mRem = patRemove.matcher(path)

        val mb = patBuffer.matcher(path)
        if(mb.matches()) {
            val interest : Int = Integer.parseInt(mb.group(1)!!)
            val off : Int = Integer.parseInt(mb.group(2)!!)
            val buffer : Int = Integer.parseInt(mb.group(3)!!)

            service.buffer(interest, off, buffer)

            ack(resp)
            return
        }

        val madd = patEntityAdd.matcher(path)
        if(madd.matches()) {
            val interest : Int = Integer.parseInt(madd.group(1)!!)
            val eid : Long = java.lang.Long.parseLong(madd.group(2)!!)
            service.add(interest, eid as Comparable<Any>)
            ack(resp)
            return
        }

        val mrem = patEntityRemove.matcher(path)
        if(mrem.matches()) {
            val interest : Int = Integer.parseInt(mrem.group(1)!!)
            val eid : Long = java.lang.Long.parseLong(mrem.group(2)!!)
            service.remove(interest, eid as Comparable<Any>)
            ack(resp)
            return
        }

        val mclear = patClear.matcher(path)
        if(mclear.matches()) {
            val interest : Int = Integer.parseInt(mrem.group(1)!!)
            service.clear(interest)
            ack(resp)
            return
        }

        val mrrem = patRelRem.matcher(path)
        if(mrrem.matches()) {
            val eid : Long = java.lang.Long.parseLong(mrrem.group(1)!!)
            val prop = mrrem.group(2)!!
            val target = java.lang.Long.parseLong(mrrem.group(3)!!)
            service.remove(eid, prop, target)
            ack(resp)
            return
        }

        val mradd = patRelAdd.matcher(path)
        if(mradd.matches()) {
            val eid : Long = java.lang.Long.parseLong(mradd.group(1)!!)
            val prop = mradd.group(2)!!
            val target : Long = java.lang.Long.parseLong(mradd.group(3)!!)
            service.add(eid, prop, target)
            ack(resp)
            return
        }

        if (mCrt.matches()) {
            val i = service.create(mCrt.group(1)!!)
            val json = om.createObjectNode()!!
            json.put("response", "ok")
            json.put("interest", i)
            resp.setContentType("application/json")
            resp.getWriter()?.print(json.toString()!!)
            resp.flushBuffer()

        } else if (mRem.matches()) {
            val iid = Integer.parseInt(mRem.group(1)!!)
            service.delete(iid)
            ack(resp)
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
        if(path=="/retrieve") {
            val ips = req.getInputStream()!!
            val f = read(ips)
            log.info("RETRIEVE: $f")

            val on = om.readTree(f)
            when(on) {
                is ArrayNode -> {val ses = KISession.current();
                    if(ses!=null) {
                        val pub = ses.entities
                        if(pub!=null)
                        service.galaxy.retriever(on.map { it.longValue() as Comparable<Any> }, pub)
                        else {
                            val context = IllegalStateException("exception to support tracking")
                            log.warn("retrieval request with no defined publisher", context)}
                    } else throw IllegalStateException("no session found")
                }
                else -> throw IllegalStateException("cant parse $f")
            }
            ack(resp)
            return
        }
        val mf = patFilter.matcher(path)
        if (mf.matches()) {
            val sint = mf.group(1)!!
            val id = Integer.parseInt(sint)

            val ips = req.getInputStream()!!
            val f = read(ips)
            log.info("FILTER: $f")

            val on = om.readTree(f)
            service.filter(id, on as ObjectNode)
            ack(resp)
            return
        }
        val ms = patSave.matcher(path)
        if(ms.matches()) {
            service.save(om.readTree(read(req.getInputStream()!!))!! as ObjectNode)
            ack(resp)
            return
        }

        val mce = patCreateEntity.matcher(path)
        if(mce.matches()) {
            val id = service.createElement(Jsonifier.valueMap(om.readTree(read(req.getInputStream()!!)) as ObjectNode, service.galaxy.descriptor))

            ack(resp, mapOf<String,Any>("id" to id))
            return
        }

        val mob = patOrder.matcher(path)
        if(mob.matches()){
            val sint = mob.group(1)!!
            val id = Integer.parseInt(sint)
            service.orderBy(id, om.readTree(read(req.getInputStream()!!)) as ArrayNode)
            ack(resp)
            return
        }

        val mact = patAction.matcher(path)
        if(mact.matches()) {
            val eid = java.lang.Long.parseLong(mact.group(1)!!)
            val action = mact.group(2)!!
            val pars = om.readTree(read(req.getInputStream()!!)) as ArrayNode
            val res = service.call<Long>(eid, action, pars)
            val fields = HashMap<String,Any>()
            if(res!=null) {
                fields["result"] =  res as Any
            }
            ack(resp, fields)
            return
        }

        throw IllegalArgumentException("unknown POST: $path")
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
    private val currentApp: ThreadLocal<KIApplication> = ThreadLocal()
    override fun service(req: HttpServletRequest?, resp: HttpServletResponse?) {
        if (req == null) throw IllegalStateException()
        if (resp == null) throw IllegalStateException()

        val httpSession = req.getSession(true)!!
        if (httpSession.getAttribute(SESSION_KEY) == null) {
            httpSession.setAttribute(SESSION_KEY, KISession(KIPrincipal.ANONYMOUS, app))
        }
        if (httpSession.getAttribute(APPLICATION_KEY) == null) {
            httpSession.setAttribute(APPLICATION_KEY, app)
        }
        val s = httpSession.getAttribute(SESSION_KEY)!! as KISession
        currentApp.set(app)
        s.current()
        log.info("service ${req.getPathInfo()}")
        try {
            super.service(req, resp)
        } catch(e: Throwable) {
            log.error("problem on $req", e)
            ack(e, resp)
        }
    }

    protected fun ack(resp:HttpServletResponse, fields:Map<String,Any> = mapOf()) {
        val js = om.createObjectNode()!!
        js.put("response", "ok")
        fields.entrySet().forEach {
            js.put(it.getKey(), om.valueToTree<JsonNode>(it.getValue()))
        }
        resp.setContentType("application/json")
        resp.getWriter()?.write(om.writeValueAsString(js)!!)
        resp.flushBuffer()
    }

    protected fun ack(e:Throwable, resp:HttpServletResponse) {
        resp.getWriter()?.write("{response: 'error', message: '${e.getMessage()}'}")
        resp.flushBuffer()
    }

    protected fun app(s: HttpSession): KIApplication? = s.getAttribute(APPLICATION_KEY) as KIApplication


    class object {
        val SESSION_KEY = "KISESSION"
        val APPLICATION_KEY = "KIAPP"
    }
}

class DumperServlet(app:KIApplication) : KIServlet(app) {
    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        val om = ObjectMapper()
        val session = KISession.current()
        val dump = om.createObjectNode()!!
        dump.put("session", session!!.id)
        dump.put("principal", session!!.principal!!.principal)
        dump.put("token", session!!.principal!!.token)
        val ia = om.createArrayNode()!!
        session.allInterests {
            val ain = om.createObjectNode()!!
            val f = it.filter as ElementFilter<*,*>
            ain.put("name", it.name)
            ain.put("id", it.id)
            ain.put("filter", f.toJson())
            ain.put("orderBy", om.valueToTree<JsonNode>(it.orderBy))
            ain.put("limit", it.limit)
            ain.put("offset", it.offset)
            ain.put("currentsize", it.currentsize)
            ain.put("estimatedsize", it.estimatedsize)
            val order = ArrayList<Comparable<*>>()
            it.orderDo { order.add(it as Comparable<*>) }
            ain.put("order", om.valueToTree<JsonNode>(order))
            ia.add(ain)
        }
        dump.put("interests", ia)
        resp?.setContentType("application/json")
        resp?.getWriter()?.write(om.writeValueAsString(dump)!!)
        resp?.flushBuffer()
    }
}

class StaticServlet(val root:File) : HttpServlet() {
    protected val log: Logger = LoggerFactory.getLogger(this.javaClass)!!
    override fun service(req: HttpServletRequest?, resp: HttpServletResponse?) {
        if (req == null) throw IllegalStateException()
        if (resp == null) throw IllegalStateException()

        val path = req.getPathInfo()!!
        val resource = File(root, path)
        val m  = Files.probeContentType(resource.toPath())
        resp.setContentType(m)
        val fin = FileInputStream(resource)
        val ba = Array<Byte>(1024) {0}
        fin.buffered(1024).copyTo(resp.getOutputStream()!!)
        resp.flushBuffer()
    }


    protected fun app(s: HttpSession): KIApplication? = s.getAttribute(APPLICATION_KEY) as KIApplication


    class object {
        val SESSION_KEY = "KISESSION"
        val APPLICATION_KEY = "KIAPP"
    }
}