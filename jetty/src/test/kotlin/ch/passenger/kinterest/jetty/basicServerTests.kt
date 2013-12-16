package ch.passenger.kinterest.jetty

import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.Request
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.client.HttpClient

/**
 * Created by svd on 10/12/13.
 */



class ServerTests {
    private val log : Logger = LoggerFactory.getLogger(javaClass<ServerTests>())!!;
    val expect = "Hello World!"

    Test
    fun testHelloWorld() {
        val server = jetty {
            connectors {
                array(serverConnector {
                    setPort(3333)
                })
            }
            handler {
                object : AbstractHandler() {

                    override fun handle(p0: String?, p1: Request?, p2: HttpServletRequest?, p3: HttpServletResponse?) {
                        p3?.setCharacterEncoding("UTF-8")
                        p3?.setContentType("text/plain")
                        val w = p3?.getWriter()!!
                        w.write(expect)
                        w.flush()
                        w.close()
                    }
                }
            }
        }

        server.start()
        val client = HttpClient()
        client.setFollowRedirects(false)
        client.start()
        val contentResponse = client.GET("http://localhost:3333")
        assert(contentResponse?.getContentAsString()==expect)
    }
}