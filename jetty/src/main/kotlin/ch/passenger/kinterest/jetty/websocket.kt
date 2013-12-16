package ch.passenger.kinterest.jetty

import org.eclipse.jetty.websocket.api.WebSocketAdapter
import javax.servlet.http.HttpSession
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.eclipse.jetty.websocket.servlet.WebSocketServlet
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import org.eclipse.jetty.websocket.api.Session
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created with IntelliJ IDEA.
 * User: Duric
 * Date: 28.07.13
 * Time: 05:12
 */

private val log : Logger = LoggerFactory.getLogger(javaClass<KIWebsocketAdapter>().getPackage()!!.getName())!!

abstract class KIWebsocketAdapter(protected val session : HttpSession?) : WebSocketAdapter() {
    protected var wssession : Session? = null


    public fun send(text: String) {
        logJetty.info("ep: ${wssession?.getRemote()} sending: $text")
        wssession?.getRemote()?.sendStringByFuture(text)
    }
}

abstract class KIWebsocketServlet(val creator : WebSocketCreator) : WebSocketServlet() {

    public override fun configure(p0: WebSocketServletFactory?) {
        p0?.setCreator(creator)
    }
}