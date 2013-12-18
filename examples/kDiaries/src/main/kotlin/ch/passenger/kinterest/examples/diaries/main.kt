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


/**
 * Created by svd on 16/12/13.
 */
val log = LoggerFactory.getLogger("diaries")
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
    val users = Universe.galaxy(javaClass<User>())!!

    val f = JFrame("Diaries")
    f.getContentPane()!!.setLayout(BorderLayout())
    val sp = JScrollPane(JTable(InterestTableModel(Interest("", javaClass<Diary>()))))
    f.getContentPane()!!.add(sp)
    f.pack()
    f.setVisible(true)

    log?.info("visible")
    //users.create(mapOf("email" to "svd@zzz.com" , "nick" to "svd"))
    val uf = UserFrame(users)
    uf.show()
}

class UserFrame(val users:Galaxy<User,Long>) {
    val tfEmail = JTextField(22)
    val tfNick = JTextField(8)
    fun show() {
        val fu = JFrame("Users")
        fu.getContentPane()!!.setLayout(BorderLayout())
        val iuser = Universe.galaxy(javaClass<User>())?.interest()!!
        iuser.filter = FilterFactory(javaClass<User>()).gte("id", 0.toLong())
        val spu = JScrollPane(JTable(InterestTableModel(iuser)))
        fu.getContentPane()!!.add(spu)
        val south = Box.createHorizontalBox()!!
        south.add(JLabel("Email:"))
        south.add(tfEmail)
        south.add(JLabel("Nick:"))
        south.add(tfNick)
        val create = object: AbstractAction("Create") {

            override fun actionPerformed(e: ActionEvent) {
                val props = mapOf("email" to (tfEmail.getText()?:""), "nick" to (tfNick.getText()?:""))
                users.create(props)
            }
        }
        south.add(Box.createHorizontalGlue())
        south.add(JButton(create))
        fu.getContentPane()?.add(south, BorderLayout.SOUTH)

        fu.pack()
        fu.setVisible(true)
    }
}