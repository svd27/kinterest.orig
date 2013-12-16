import ch.passenger.kinterest.neo4j.Neo4jDatastore
import org.neo4j.graphdb.Node
import ch.passenger.kinterest.testdomain.TestA

import ch.passenger.kinterest.Galaxy
import ch.passenger.kinterest.UpdateEvent
import rx.subjects.PublishSubject
import ch.passenger.kinterest.Universe
import org.neo4j.graphdb.GraphDatabaseService
import ch.passenger.kinterest.neo4j.Neo4jDbWrapper
import ch.passenger.kinterest.testdomain.TestB
import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.util.InterestList
import ch.passenger.kinterest.Interest

class TestATempl( val id:Long, val store : Neo4jDatastore<Long>, val node:Node) :  TestA {
    protected fun<T> prop(name: String): T? = store.tx { node().getProperty(name) as T? }
    protected fun<T> prop(name: String, value: T?): Unit = store.tx { node().setProperty(name, value) }

    protected inline fun node(): Node = node

    public fun get(p: String): Any? = prop(p)
    public fun set(p: String, value: Any?): Unit = prop(p, value)
    override fun id(): Long = id
    override val name: String get() = node().getProperty("name") as String
    override var options: String get() = prop("options")!!
        set(v) = prop("options", v)

    override var ami: String?
      get() = prop("ami")
    set(v) = prop("ami", v)


    override protected val subject = subject()

    override var lots: Double = 0.0


    override var mustbeset: Double = 0.0


    override val optionalWeights: Interest<TestB, Long> = Interest<TestB,Long>("", javaClass<TestB>())

    class object {
        val kind = "ch.passenger.kinterest.testdomain.TestA"
        fun create(name:String, weight:TestB?, store:Neo4jDatastore<Long>) : TestA {
            return store.tx<TestA> {
                val node = store.create(store.nextSequence(kind), kind) {
                it.setProperty("name", name)
                it.setProperty("options", "")
                }

                val res = TestATempl(node.getProperty("ID") as Long, store, node)
                store.setRelation(res, weight, "weight", true)
                res
            }
        }
        fun get(id:Long, store:Neo4jDatastore<Long>) : TestA? {
            val node = store.node(id, kind)
            if(node!=null) return TestATempl(id, store, node)
            return null
        }
        val galaxy : Galaxy<TestA,Long> get() = Universe.galaxy(javaClass<TestA>())!!
    }


    override val weight: TestB?
        get() {
            val l = TestATempl.galaxy.relation(id, javaClass<TestB>(), "weight")
            if (l != null)
                return Universe.get(javaClass<TestB>(), l)
            return null
        }

}

class TestAGalaxyTemplate(val neo4j:Neo4jDatastore<Long>) : Galaxy<TestA,Long>(javaClass<TestA>(), neo4j) {
    override fun generateId(): Long = neo4j.nextSequence(kind) as Long
    override fun retrieve(id: Long): TestA? = TestATempl.get(id, neo4j)
    override fun create(values:Map<String,Any?>) : TestA {
        val n = neo4j.create(neo4j.nextSequence(kind),kind) {
            values.entrySet().forEach {
                e ->
                if(e.getKey()!="ID" && e.getKey()!="KIND") {
                    it.setProperty(e.getKey(), e.getValue())
                }
            }
        }
        return TestATempl.get(n.getProperty("ID") as Long, neo4j)!!
    }
}

public fun boostrapTestATemp(db:Neo4jDbWrapper) {
    Universe.register(TestAGalaxyTemplate(Neo4jDatastore(db)))
}


