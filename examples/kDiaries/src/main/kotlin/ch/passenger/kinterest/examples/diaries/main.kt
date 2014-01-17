package ch.passenger.kinterest.examples.diaries

import ch.passenger.kinterest.Universe
import javax.swing.JFrame
import java.awt.BorderLayout
import javax.swing.JScrollPane
import javax.swing.JTable
import ch.passenger.kinterest.util.swing.InterestTableModel
import ch.passenger.kinterest.Interest
import ch.passenger.kinterest.neo4j.Neo4jDbWrapper
import org.slf4j.LoggerFactory
import ch.passenger.kinterest.PropertyFilter
import ch.passenger.kinterest.FilterFactory
import org.neo4j.kernel.GraphDatabaseAPI
import javax.swing.JPanel
import javax.swing.Box
import javax.swing.JTextField
import javax.swing.JLabel
import javax.swing.AbstractAction
import java.awt.event.ActionEvent
import javax.swing.JButton
import ch.passenger.kinterest.Galaxy
import java.util.logging.Logger
import java.util.logging.Level
import javax.swing.table.TableRowSorter
import ch.passenger.kinterest.service.InterestService
import com.fasterxml.jackson.databind.ObjectMapper
import ch.passenger.kinterest.util.json.Jsonifier
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import ch.passenger.kinterest.SortKey
import ch.passenger.kinterest.SortDirection
import java.util.ArrayList
import ch.passenger.kinterest.oppositeSortDirection
import javax.swing.JFormattedTextField
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import ch.passenger.kinterest.service.KIApplication
import ch.passenger.kinterest.jetty.*
import ch.passenger.kinterest.service.SimpleServiceDescriptor
import ch.passenger.kinterest.service.KISession
import ch.passenger.kinterest.service.KIPrincipal
import rx.plugins.RxJavaErrorHandler
import ch.passenger.kinterest.entityName


/**
 * Created by svd on 16/12/13.
 */
val log = LoggerFactory.getLogger("diaries")!!

val app = KIApplication("diaries", listOf(
        SimpleServiceDescriptor(javaClass<InterestService<DiaryOwner,Long>>()) {
            InterestService(Universe.galaxy(javaClass<DiaryOwner>().entityName())!!)
        },
        SimpleServiceDescriptor(javaClass<InterestService<Diary,Long>>()) {
            InterestService(Universe.galaxy(javaClass<Diary>().entityName())!!)
        },
        SimpleServiceDescriptor(javaClass<InterestService<DiaryDayEntry,Long>>()) {
            InterestService(Universe.galaxy(javaClass<DiaryDayEntry>().entityName())!!)
        }
))

val session = KISession(KIPrincipal.ANONYMOUS, app)
public fun main(args: Array<String>) {
    /*
Logger.getLogger("").getHandlers().forEach {
    it.setLevel(Level.FINE)
}
*/
    Logger.getLogger("org.eclipse").setLevel(Level.FINE)
//Logger.getLogger("").setLevel(Level.FINE)
    rx.plugins.RxJavaPlugins.getInstance()!!.registerErrorHandler(object : RxJavaErrorHandler() {
        private final val log = LoggerFactory.getLogger("RXERRORS")!!
        override fun handleError(e: Throwable?) {
            log.error("RX ERROR: ", e)
            e?.printStackTrace()
        }
    })
    val db = org.neo4j.graphdb.factory.GraphDatabaseFactory().newEmbeddedDatabase("./neo/data")
    val api : GraphDatabaseAPI = db as GraphDatabaseAPI
    val srv = org.neo4j.server.WrappingNeoServer(api)
    srv.start()

    boostrapDomain(Neo4jDbWrapper(db))



    jetty {
        connectors {
            array(serverConnector {
                setPort(3333)
            })
        }

        servlets {
            AppServlet(app).init(this)
        }
    }.start()


    session.current()

    val diaries = Universe.galaxy<Diary,Long>(javaClass<Diary>().entityName())!!
    val users = Universe.galaxy<DiaryOwner,Long>(javaClass<DiaryOwner>().entityName())!!
    val service = InterestService(users)
    val sdiary = InterestService(diaries)

    val f = JFrame("Diaries")
    f.getContentPane()!!.setLayout(BorderLayout())
    val sp = JScrollPane(JTable(InterestTableModel(Interest("", javaClass<Diary>()), sdiary)))
    f.getContentPane()!!.add(sp)
    f.pack()
    f.setVisible(true)

    log?.info("visible")
    //users.create(mapOf("email" to "svd@zzz.com" , "nick" to "svd"))
    session.current()
    val uf = UserFrame(service)
    uf.show()
}

class UserFrame(val users:InterestService<DiaryOwner,Long>) {
    val tfEmail = JTextField(22)
    val tfNick = JTextField(8)
    val iuser = users.create("")
    fun show() {
        val fu = JFrame("Users")
        fu.getContentPane()!!.setLayout(BorderLayout())

        users.filter(iuser, FilterFactory(Universe.galaxy<DiaryOwner,Long>("DiaryOwner") as Galaxy<DiaryOwner,Long>, javaClass<DiaryOwner>(), users.galaxy.descriptor).gte("id", 0.toLong()).toJson())
        val galaxy = Universe.galaxy<DiaryOwner,Long>(javaClass<DiaryOwner>().entityName())!!
        var ain :Interest<DiaryOwner,Long>? = null
        galaxy.withInterestDo {
            ain = it[iuser]
        }
        val tbl = JTable(InterestTableModel(ain!!, users))
        tbl.getTableHeader()?.addMouseListener(object : MouseAdapter() {

            override fun mouseClicked(e: MouseEvent) {
                val add = e.isShiftDown()
                val idx = tbl.convertColumnIndexToModel(tbl.columnAtPoint(e.getPoint()))
                val m = tbl.getModel() as InterestTableModel<DiaryOwner,Long>
                val col = m.columnAt(idx)
                if(col!=null) {
                    val ain = ain!!
                    if(!add) {
                        if(ain.orderBy.size==1 && ain.orderBy[0]?.property==col.property) {
                            val sortDirection = ain.orderBy[0].direction
                            val nk = SortKey(col.property, if(sortDirection==SortDirection.ASC) SortDirection.DESC else SortDirection.ASC)
                            ain.orderBy = array(nk)
                        } else {
                            ain.orderBy = array(SortKey(col.property, SortDirection.ASC))
                        }
                    } else {
                        log.info("additive sort ${col.property}")
                        val no = ArrayList<SortKey>()
                        var found = false
                        ain.orderBy.forEach {
                            if(it.property!=col.property) no.add(it)
                            else {
                                found = true
                                log.info("turning direction ${col.property}")
                                 no.add(SortKey(it.property, oppositeSortDirection(it.direction)))
                            }
                        }
                        if(!found) no.add(SortKey(col.property, SortDirection.ASC))
                        ain.orderBy = Array(no.size) {no[it]}
                    }
                }
            }
        })
        val spu = JScrollPane(tbl)
        fu.getContentPane()!!.add(spu)
        val south = Box.createVerticalBox()
        val south1 = Box.createHorizontalBox()!!
        south1.add(JLabel("Email:"))
        south1.add(tfEmail)
        south1.add(JLabel("Nick:"))
        south1.add(tfNick)
        val create = object: AbstractAction("Create") {

            override fun actionPerformed(e: ActionEvent) {
                val om = ObjectMapper()
                val json = om.createObjectNode()!!
                json.put("email", tfEmail.getText()?:"")
                json.put("nick", tfNick.getText()?:"")
                val props = mapOf("email" to (tfEmail.getText()?:""), "nick" to (tfNick.getText()?:""))
                users.createElement(props)
            }
        }
        south1.add(Box.createHorizontalGlue())
        south1.add(JButton(create))
        val south2 = Box.createHorizontalBox()!!
        south2.add(JLabel("Page Size: "))
        val tfPagesize = JFormattedTextField(0.toInt())
        tfPagesize.addFocusListener(object : FocusAdapter() {

            override fun focusLost(e: FocusEvent) {
                val v = (tfPagesize.getValue() as Number).toInt()

                if(v<1) {
                    users.buffer(iuser, 0, 0)
                } else {
                    users.buffer(iuser, 0, v)
                }
            }
        })
        south2.add(tfPagesize)
        val prev = object : AbstractAction("<") {

            override fun actionPerformed(e: ActionEvent) {
                val ain = ain!!
                ain.buffer(Math.max(ain.offset-ain.limit,0), ain.limit)
            }
        }
        prev.setEnabled(false)
        val next = object : AbstractAction(">") {

            override fun actionPerformed(e: ActionEvent) {
                val ain = ain!!
                ain!!.buffer(if(ain.offset+ain.limit>ain.estimatedsize) ain.estimatedsize-ain.limit else ain.offset+ain.limit, ain.limit)
            }
        }
        next.setEnabled(false)
        south2.add(JButton(prev))
        south2.add(JButton(next))
        south.add(south1)
        south.add(south2)
        fu.getContentPane()?.add(south, BorderLayout.SOUTH)

        tbl.getModel()?.addTableModelListener {
            val ain = ain!!
            log.info("TME: sz: ${ain.estimatedsize} off: ${ain.offset} lim: ${ain.limit}")
            if(ain.offset+ain.limit<=   ain.estimatedsize) {
                next.setEnabled(true)
            } else next.setEnabled(false)
            if(ain.offset>0) {
                prev.setEnabled(true)
            } else prev.setEnabled(false)
        }

        fu.pack()
        fu.setVisible(true)
    }
}