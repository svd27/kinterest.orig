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


/**
 * Created by svd on 16/12/13.
 */
val log = LoggerFactory.getLogger("diaries")!!
public fun main(args: Array<String>) {
    /*
Logger.getLogger("").getHandlers().forEach {
    it.setLevel(Level.FINE)
}
    Logger.getLogger("org.eclipse").setLevel(Level.INFO)
Logger.getLogger("").setLevel(Level.FINE)
*/
    val db = org.neo4j.graphdb.factory.GraphDatabaseFactory().newEmbeddedDatabase("./neo/data")
    val api : GraphDatabaseAPI = db as GraphDatabaseAPI
    val srv = org.neo4j.server.WrappingNeoServer(api)
    srv.start()

    boostrapDomain(Neo4jDbWrapper(db))
    val diaries = Universe.galaxy(javaClass<Diary>())!!
    val users = Universe.galaxy(javaClass<DiaryOwner>())!!
    val service = InterestService("users", users)
    val sdiary = InterestService("diaries", diaries)

    val f = JFrame("Diaries")
    f.getContentPane()!!.setLayout(BorderLayout())
    val sp = JScrollPane(JTable(InterestTableModel(Interest("", javaClass<Diary>()), sdiary)))
    f.getContentPane()!!.add(sp)
    f.pack()
    f.setVisible(true)

    log?.info("visible")
    //users.create(mapOf("email" to "svd@zzz.com" , "nick" to "svd"))
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

        users.query(iuser, FilterFactory(javaClass<DiaryOwner>()).gte("id", 0.toLong()))
        val tbl = JTable(InterestTableModel(iuser, users))
        tbl.getTableHeader()?.addMouseListener(object : MouseAdapter() {

            override fun mouseClicked(e: MouseEvent) {
                val add = e.isShiftDown()
                val idx = tbl.convertColumnIndexToModel(tbl.columnAtPoint(e.getPoint()))
                val m = tbl.getModel() as InterestTableModel<DiaryOwner,Long>
                val col = m.columnAt(idx)
                if(col!=null) {
                    if(!add) {
                        if(iuser.orderBy.size==1 && iuser.orderBy[0]?.property==col.property) {
                            val sortDirection = iuser.orderBy[0].direction
                            val nk = SortKey(col.property, if(sortDirection==SortDirection.ASC) SortDirection.DESC else SortDirection.ASC)
                            iuser.orderBy = array(nk)
                        } else {
                            iuser.orderBy = array(SortKey(col.property, SortDirection.ASC))
                        }
                    } else {
                        log.info("additive sort ${col.property}")
                        val no = ArrayList<SortKey>()
                        var found = false
                        iuser.orderBy.forEach {
                            if(it.property!=col.property) no.add(it)
                            else {
                                found = true
                                log.info("turning direction ${col.property}")
                                no.add(SortKey(it.property, oppositeSortDirection(it.direction)))
                            }
                        }
                        if(!found) no.add(SortKey(col.property, SortDirection.ASC))
                        iuser.orderBy = Array(no.size) {no[it]}
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
                users.createElement(Jsonifier.valueMap(json, iuser.descriptor))
            }
        }
        south1.add(Box.createHorizontalGlue())
        south1.add(JButton(create))
        val south2 = Box.createHorizontalBox()!!
        south2.add(JLabel("Page Size: "))
        val tfPagesize = JFormattedTextField(iuser.limit)
        tfPagesize.addFocusListener(object : FocusAdapter() {

            override fun focusLost(e: FocusEvent) {
                val v = (tfPagesize.getValue() as Number).toInt()
                if(v==iuser.limit) return
                if(v<1) {
                    iuser.limit = 0
                    iuser.offset = 0
                } else {
                    iuser.limit = v
                    iuser.offset = 0
                }
            }
        })
        south2.add(tfPagesize)
        val prev = object : AbstractAction("<") {

            override fun actionPerformed(e: ActionEvent) {
                iuser.offset = Math.max(iuser.offset-iuser.limit,0)
            }
        }
        prev.setEnabled(false)
        val next = object : AbstractAction(">") {

            override fun actionPerformed(e: ActionEvent) {
                iuser.offset = if(iuser.offset+iuser.limit>iuser.size) iuser.size-iuser.limit else iuser.offset+iuser.limit
            }
        }
        next.setEnabled(false)
        south2.add(JButton(prev))
        south2.add(JButton(next))
        south.add(south1)
        south.add(south2)
        fu.getContentPane()?.add(south, BorderLayout.SOUTH)

        tbl.getModel()?.addTableModelListener {
            log.info("TME: sz: ${iuser.size} off: ${iuser.offset} lim: ${iuser.limit}")
            if(iuser.offset+iuser.limit<=   iuser.size) {
                next.setEnabled(true)
            } else next.setEnabled(false)
            if(iuser.offset>0) {
                prev.setEnabled(true)
            } else prev.setEnabled(false)
        }

        fu.pack()
        fu.setVisible(true)
    }
}