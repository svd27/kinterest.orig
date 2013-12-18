/*
package ch.passenger.kinterest.neo4j

import org.junit.Before
import org.junit.After
import org.neo4j.test.TestGraphDatabaseFactory
import org.neo4j.graphdb.GraphDatabaseService
import org.junit.Test
import org.neo4j.graphdb.Transaction
import org.slf4j.LoggerFactory
import org.neo4j.graphdb.Node
import ch.passenger.kinterest.LivingElement
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Column
import ch.passenger.kinterest.annotations.DefaultValue
import ch.passenger.kinterest.annotations.DefaultsTo
import ch.passenger.kinterest.util.slf4j.info
import org.neo4j.tooling.GlobalGraphOperations
import ch.passenger.kinterest.neo4j.*
import ch.passenger.kinterest.testdomain.TestA
import ch.passenger.kinterest.FilterFactory
import rx.Observable
import org.neo4j.kernel.GraphDatabaseAPI
import org.neo4j.server.WrappingNeoServer
import ch.passenger.kinterest.SortKey
import ch.passenger.kinterest.SortDirection
import ch.passenger.kinterest.util.with
import ch.passenger.kinterest.Universe
import ch.passenger.kinterest.testdomain.TestB
import kotlin.test.assertEquals
import javax.swing.JOptionPane
import kotlin.test.assertNotNull


*/
/**
 * Created by svd on 12/12/13.
 *//*



open class BasicNeo4jTest() {
    var log = LoggerFactory.getLogger(this.javaClass)!!
    var db: GraphDatabaseService? = null
    var srv : WrappingNeoServer? = null

    Before
    fun setup() {
        db = TestGraphDatabaseFactory().newImpermanentDatabase()
        srv = org.neo4j.server.WrappingNeoServer(db as GraphDatabaseAPI)
        srv?.start()
        boostrapDomain(Neo4jDbWrapper(db!!))
    }

    After
    fun teardown() {
        //print("<RET> to exit...")
        //readLine()
        srv?.stop()
        db?.shutdown()
    }


    Test
    fun testSimpleInsert() {
        val tx: Transaction = db!!.beginTx()!!

        tx.with {
            val n = db!!.createNode()!!;
            n.setProperty("name", "Nancy");
            tx.success();
            dump(n)
        }
    }
    Test
    fun testDomain() {
        val store = Neo4jDatastore<Long>(Neo4jDbWrapper(db!!))
        val a1 = TestAImpl.create(1,null,0.0, "a1", store)
        log.info ("a1 created: ${a1.id()} ${a1.name}")

        store.tx {
            log.info ("dump all" )
            GlobalGraphOperations.at(db!!)?.getAllNodes()?.forEach {
                dump(it)
            }
        }
        a1.lots = 9.9
        dump(TestAImpl.get(1, store)!!)
        assert(TestAImpl.get(1, store)?.lots == 9.9)
    }

    Test
    fun testFilter() {
        val store = Neo4jDatastore<Long>(Neo4jDbWrapper(db!!))
        for(i in 100..120) {
            TestAImpl.create(i.toLong(), null, Math.random(), "test-${i}", store)
        }
        val ff : FilterFactory<TestA,Long> = FilterFactory(javaClass<TestA>())


        val observable : Observable<Long> = store.filter(ff.eq("name", "test-111"), Array<SortKey>(0) {SortKey("", SortDirection.ASC)}, -1, -1)
        val blockingObservable = observable.toBlockingObservable()
        blockingObservable?.forEach {
            l -> log.info("$l"); assert(l==111.toLong())
        }
        //observable.forEach {
        //    log.info("$it")
        //}


        //store.tx {  db?.getAllNodes()?.forEach { dump(it) } }
    }

    Test()
    fun testOneToOne() {
        val store = Neo4jDatastore<Long>(Neo4jDbWrapper(db!!))
        TestBImpl.create(1, .5, "1B", "hphp", store)

        val testB : TestB = TestBImpl.get(1, store)!!
        TestAImpl.create(1,testB,.33,"1A", store)
        dump(testB)
        assert(testB.weight==.5)
        assertEquals("1B",testB.name)
        val testA = Universe.get(javaClass<TestA>(), 1)!!
        assertEquals(1.toLong(), testA.id())
        assertEquals(1.toLong(), testB.id())
        assertEquals(testA.mustbeset,.33.toDouble())

        assertNotNull(testA.weight)
        assertEquals("1B", testA.weight?.name)
        testA.optionalWeights.add(testB)
    }

    fun dump(tb:TestB) = "${tb.id()}: ${tb.weight} ${tb.comment}"

    fun dump(ta:TestA) {
        log.info {
            """
            ${ta.id()}: name: ${ta.name} lots: ${ta.lots}
            """
        }
    }

    fun dump(n:Node) {
        log.info("Node: ${n.getId()}\n")
        n.getLabels()!!.forEach {
            log.info("label: ${it.name()}\n")
        }
        n.getPropertyKeys()!!.forEach {
            log.info("$it -> ${n.getProperty(it)}\n")
        }
    }
}*/
