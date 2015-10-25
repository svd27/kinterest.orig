package ch.passenger.kinterest.jetty

import org.eclipse.jetty.server.*
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpSession

/**
 * Created by sdju on 25.07.13.
 */

val logJetty = LoggerFactory.getLogger("ch.passenger.kinterest.jetty")!!

fun jetty(cfg : Server.()->Unit) : Server {
    val server = Server()

    server.cfg()

    return server
}

fun Server.connectors(cfg : Server.() -> Array<Connector>) {
    setConnectors(cfg())
}

fun Server.serverConnector(cfg : ServerConnector.() -> Unit) : Connector {
    val c = ServerConnector(this)
    c.cfg()
    return c
}

fun Server.handler(cfg : Server.() -> Handler) {
    setHandler(cfg())
}

fun Server.servlets(cfg:ServletContextHandler.()->Unit) {
    val ctx = ServletContextHandler(ServletContextHandler.SESSIONS)
    ctx.cfg()
    setHandler(ctx)
}


fun ServletContextHandler.servlets(cfg : ServletContextHandler.() -> Map<String,ServletHolder>) {
    cfg().entrySet().forEach { addServlet(it.value, it.key) }
}

fun ServletContextHandler.plus(p:Pair<String,ServletHolder>) {
    addServlet(p.second, p.first)
}


fun ServletContextHandler.socket(cfg : ServletContextHandler.() -> Pair<String,Class<KIWebsocketAdapter>>) {
    val pair = cfg()
    val wsc = WebSocketCreator {
        req,resp ->
        val ctor = pair.second.getConstructor(javaClass<HttpSession>())
        logJetty.info("create ws ${ctor} ${req} ${req?.getSession()}")
        ctor.newInstance(req?.getSession()!!)
    }
    //class WSSServlet() : KIWebsocketServlet(wsc)
    val sh = ServletHolder(object : KIWebsocketServlet(wsc){})
    logJetty.info("WS: register $sh")
    addServlet(sh, pair.first)
}


fun ServletContextHandler.asocket(cfg : ServletContextHandler.() -> Pair<String,(HttpSession)->KIWebsocketAdapter>) {
    val pair = cfg()
    val wsc = WebSocketCreator {
        req,resp ->
        pair.second(req!!.getSession() as HttpSession)
    }
    class WSSServlet() : KIWebsocketServlet(wsc)
    val sh = ServletHolder(object : KIWebsocketServlet(wsc){})
    addServlet(sh, pair.first)
}


fun AbstractNetworkConnector.configure(cfg : AbstractNetworkConnector.()->Unit) {
    cfg()
}
