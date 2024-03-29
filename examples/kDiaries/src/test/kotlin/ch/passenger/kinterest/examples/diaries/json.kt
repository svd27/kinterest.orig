package ch.passenger.kinterest.examples.diaries

import ch.passenger.kinterest.ElementEvent
import ch.passenger.kinterest.FilterFactory
import ch.passenger.kinterest.Universe
import ch.passenger.kinterest.entityName
import org.junit.Before
import org.junit.Test
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.test.TestGraphDatabaseFactory
import rx.Observer
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Created by svd on 19/12/13.
 */
class DiaryJsonTests {
    var db : GraphDatabaseService? = null
    @Before
    fun setup() {
        Logger.getLogger("org.neo4j").setLevel(Level.FINE)
        db = TestGraphDatabaseFactory().newImpermanentDatabase()
        //boostrapDomain(Neo4jDbWrapper(db!!))
    }

    fun teardown() {
        db!!.shutdown()
    }

    @Test
    fun jsonify() {
        val guser = Universe.galaxy<DiaryOwner,Long>(javaClass<DiaryOwner>().entityName())!!

        val ff= FilterFactory<DiaryOwner,Long>(guser, javaClass<DiaryOwner>(), guser.descriptor)
        val i1 = guser.interested("test")
        i1.filter = ff.gte("id", 0.toLong())
        val obs = object : Observer<ElementEvent<Long>> {

            override fun onCompleted() {
                println("Done.")
            }
            override fun onError(e: Throwable?) {
                e?.printStackTrace()
            }
            override fun onNext(args: ElementEvent<Long>?) {
                val el = guser.get(args!!.id);
                //println(Jsonifier.jsonify(el!!, guser.descriptor).asText())
            }
        }
        i1.observable.filter { println(">>> $it <<<"); it is ElementEvent<Long>}?.cast(javaClass<ElementEvent<Long>>())?.subscribe(obs)

        guser.create(mapOf("email" to "a@b.c", "name" to "aaa"))

    }
}