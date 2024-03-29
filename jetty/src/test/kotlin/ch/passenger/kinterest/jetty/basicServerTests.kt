package ch.passenger.kinterest.jetty

import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Created by svd on 10/12/13.
 */



class ServerTests {
    private val log : Logger = LoggerFactory.getLogger(ServerTests::class.java)!!;
    val expect = "Hello World!"

    @Test
    fun testHelloWorld() {
        val server = jetty {
            connectors {
                arrayOf(serverConnector {
                    setPort(3333)
                })
            }
            handler {
                object : AbstractHandler() {

                    override fun handle(p0: String?, p1: Request?, p2: HttpServletRequest?, p3: HttpServletResponse?) {
                        p3?.characterEncoding = "UTF-8"
                        p3?.contentType = "text/plain"
                        val w = p3?.writer!!
                        w.write(expect)
                        w.flush()
                        w.close()
                    }
                }
            }
        }

        server.start()
        val client = HttpClient()
        client.isFollowRedirects = false
        client.start()
        val contentResponse = client.GET("http://localhost:3333")
        assert(contentResponse?.contentAsString ==expect)
    }
}