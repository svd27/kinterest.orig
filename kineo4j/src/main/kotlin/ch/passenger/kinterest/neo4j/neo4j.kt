package ch.passenger.kinterest.neo4j

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.neo4j.graphdb.GraphDatabaseService
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Arrays
import java.lang.reflect.Modifier
import java.io.File
import javassist.ClassPool
import java.io.FileInputStream
import javax.persistence.Entity
import javassist.CtClass
import javassist.CtMethod
import java.util.HashMap
import javax.persistence.Id
import org.jetbrains.annotations.NotNull
import ch.passenger.kinterest.annotations.DefaultValue
import java.io.FileWriter
import java.io.Writer
import java.io.StringWriter
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.ResourceIterable
import org.joda.time.DateTime
import org.neo4j.graphdb.Transaction
import ch.passenger.kinterest.util.slf4j.info
import ch.passenger.kinterest.LivingElement
import org.jetbrains.annotations.Nullable
import ch.passenger.kinterest.util.slf4j.*
import org.neo4j.graphdb.event.TransactionEventHandler
import org.neo4j.graphdb.event.TransactionData
import rx.Observable
import ch.passenger.kinterest.Event
import rx.Subscription
import rx.subjects.Subject
import rx.subjects.PublishSubject
import ch.passenger.kinterest.CreateEvent
import ch.passenger.kinterest.UpdateEvent
import rx.Observer
import ch.passenger.kinterest.DataStore
import ch.passenger.kinterest.ElementFilter
import org.neo4j.cypher.javacompat.ExecutionEngine
import rx.subjects.AsyncSubject
import org.neo4j.cypher.javacompat.ExecutionResult
import java.util.concurrent.Executors
import ch.passenger.kinterest.CombinationFilter
import ch.passenger.kinterest.FilterRelations
import ch.passenger.kinterest.PropertyFilter
import ch.passenger.kinterest.SortKey
import ch.passenger.kinterest.SortDirection
import ch.passenger.kinterest.DomainObject
import ch.passenger.kinterest.DeleteEvent
import javax.persistence.Transient
import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.util.with
import javax.persistence.OneToOne
import javax.persistence.OneToMany
import ch.passenger.kinterest.DomainObjectDescriptor
import ch.passenger.kinterest.util.filter
import org.neo4j.cypher.CypherExecutionException
import org.neo4j.kernel.api.exceptions.schema.AlreadyIndexedException
import rx.concurrency.ExecutorScheduler

/**
 * Created by svd on 12/12/13.
 */

val log = LoggerFactory.getLogger("ch.passenger.kinterest.neo4j")!!

public fun<T> Transaction.use(tx: Transaction.() -> T): T {
    log.info("tx start")
    try {
        val res = tx()
        success()
        return res
    } finally {
        log.info("tx close")
        this.close()
    }
}


class Neo4jDbWrapper(val db: GraphDatabaseService) {
    val engine: ExecutionEngine = ExecutionEngine(db)
}

class Neo4jDatastore<U : Hashable>(val db: Neo4jDbWrapper) : DataStore<Event<U>, U> {
    private val log = LoggerFactory.getLogger(javaClass<Neo4jDatastore<U>>())!!;
    private val subject: PublishSubject<Event<U>> = PublishSubject.create()!!;
    private val engine = db.engine;

    {
        db.db.registerTransactionEventHandler(object : TransactionEventHandler.Adapter<Node>() {
            override fun afterCommit(data: TransactionData?, state: Node?) {
                if (data == null) return
                data.createdNodes()?.forEach {
                    if ((it?.hasProperty("ID"))?:false && !it?.hasProperty("KIND")?:false) {
                        val nkind = it.getProperty("KIND")?.toString()
                        log.info("created node ${it.getId()}:$nkind")
                        if (nkind != null)
                            subject.onNext(CreateEvent(nkind, it.getProperty("ID") as U))
                    }
                }
                data.deletedNodes()?.forEach {
                    if (it?.hasProperty("ID")?:false && !it?.hasProperty("KIND")?:false) {
                        log.info("deleted node ${it.getId()}")
                        val nkind = it.getProperty("KIND")?.toString()
                        if (nkind != null)
                            subject.onNext(DeleteEvent(nkind, it.getProperty("ID") as U))
                    }
                }
                data.assignedNodeProperties()?.forEach {
                    if (it.entity()?.hasProperty("ID")?:false && it.entity()?.hasProperty("KIND")?:false) {
                        log.info("prop change: ${it.entity()?.getId()}.${it.key()}: ${it.previouslyCommitedValue()} -> ${it.value()}")
                        val nkind = it.entity()?.getProperty("KIND")?.toString()
                        if (nkind != null)
                            subject.onNext(UpdateEvent(nkind, it.entity()?.getProperty("ID") as U,
                                    it.key()!!, it.value(), it.previouslyCommitedValue()))
                    }
                }
                data.removedNodeProperties()?.forEach {
                    if (it?.entity()?.hasProperty("ID")?:false && it?.entity()?.hasProperty("KIND")?:false) {
                        val nkind = it.entity()?.getProperty("KIND")?.toString()
                        if (nkind != null)
                            subject.onNext(UpdateEvent(nkind, it.entity()?.getProperty("ID") as U,
                                    it.key()!!, null, it.previouslyCommitedValue()))
                    }
                }
                data.createdRelationships()?.forEach {
                    val from = it.getStartNode()!!
                    if (from.hasProperty("ID") && from.hasProperty("KIND")) {
                        val nkind = from?.getProperty("KIND")?.toString()
                        val toid = it.getEndNode()?.getProperty("ID")
                        if (nkind != null)
                            subject.onNext(UpdateEvent(nkind, from?.getProperty("ID") as U, it.getType()?.name()!!, toid, null))
                    }
                }

            }
        })
    }

    private val filterFactory: Neo4jFilterFactory = Neo4jFilterFactory()
    private val pool = Executors.newFixedThreadPool(4)

    public override fun<T : LivingElement<U>> filter(f: ElementFilter<T, U>, orderBy: Array<SortKey>, offset: Int, limit: Int): Observable<U> {
        return Observable.create<U> {
            obs ->
            log.info("subscribe $obs")
            val fut = pool.submit {
                log.info("convert $f")
                val q = filterFactory.convert(f, orderBy, offset, limit)
                log.info("starting query: ${q.q} ${q.params}")
                try {
                    tx {
                        val res = engine.execute(q.q, q.params)
                        //success()
                        log.info("${res!!.executionPlanDescription()}")

                        val stats = res!!.getQueryStatistics()
                        log.info("stats: ${stats}")
                        res.forEach {
                            val id = it["ID"]
                            if (id != null)
                                obs?.onNext(id as U?)
                        }
                    }
                } catch(e: InterruptedException) {
                    log.error("filter interupted", e)
                } catch(e: Throwable) {
                    log.error("ooops", e)
                    obs?.onError(e)
                }finally {
                    obs?.onCompleted()
                }
            }
            object : Subscription {

                override fun unsubscribe() {
                    fut.cancel(true)
                }
            }
        }!!

    }

    val exec = Executors.newFixedThreadPool(8)

    override val observable: Observable<Event<U>> get() = subject.observeOn(ExecutorScheduler(exec))!!

    public fun  node(id: U, kind: String): Node? {
        val m = mapOf("oid" to id)
        val q = """
        MATCH (n:$kind) WHERE n.ID = {oid}
        RETURN n
        """
        val res = engine.execute(q, m)!!
        val l = res.columnAs<Node>("n")!!.toList()
        log.info { "get result: ${l}" }
        l.forEach { log.info(it.dump()) }
        if (l.size > 1) throw IllegalStateException()

        return if (l.size == 1) l[0] else null
    }


    override fun create(id: U, values: Map<String, Any?>, descriptor: DomainObjectDescriptor) {
        val um: MutableMap<String, Any?> = HashMap()
        values.entrySet().filter { descriptor.uniques.containsItem(it.key) }.map { it.key to it.value }.forEach { um.putAll(it) }
        val setter = values.entrySet().filter { !descriptor.uniques.containsItem(it.key) }.map { it.key }
        val kind = descriptor.entity
        tx {
            val inits = values.map { "${it.getKey()}: {${it.getKey()}}" }.reduce { a, b -> "$a, $b" }
            val q: String? = """
            MERGE (n:$kind {ID: {id}, KIND: "${descriptor.entity}"${if (inits.length > 0) ", " else ""} $inits})
            RETURN n
            """;
            val m: MutableMap<String, Any> = HashMap()
            values.entrySet().forEach {
                val v = it.value
                if (v != null) m[it.key] = v
                else m[it.key] = "NULL"
            }
            m["id"] = id
            log.info("execute $q $m")
            val res = engine.execute(q, m)!!
            val l = res.columnAs<Node>("n")!!.toList()
            log.info { "create result: ${l}" }
            l.forEach { log.info(it.dump()) }
            if (l.size > 1) throw IllegalStateException()

            //log.info ("created: " + node.dump() )
            success()
        }
        subject.onNext(CreateEvent(descriptor.entity, id))
    }
    public fun<T> tx(work: Transaction.() -> T): T {
        val t = db.db.beginTx()!!
        try {
            val res = t.work()
            t.success()
            return res
        } finally {
            t.close()
        }
    }

    public fun nextSequence(kind: String): Long {
        return tx {
            val merge = """
            MERGE (n:${kind}Sequence)
            ON CREATE n SET n.seq = 0
            ON MATCH n SET n.seq = n.seq+1
            RETURN n.seq as SEQ
            """
            val res: ExecutionResult = engine.execute(merge, mapOf())!!
            val resourceIterator = res.columnAs<Long>("SEQ")!!
            if (!resourceIterator.hasNext()) throw IllegalStateException()
            val id = resourceIterator.next()
            resourceIterator.close()
            id
        }
    }


    private fun labelFor(el: LivingElement<*>): String {
        return labelForClass(el.javaClass)
    }

    private fun labelForClass(el: Class<LivingElement<*>>): String {
        val ann = el.getAnnotation(javaClass<Entity>())
        if (ann != null && ann.name().isNotEmpty()) {
            return ann.name()!!
        }
        return el.getName()
    }

    override fun <A : LivingElement<U>, B : LivingElement<V>, V : Hashable> setRelation(from: A, to: B?, old: B?, relation: String, optional: Boolean, desc: DomainObjectDescriptor) {
        if (to == null) {
            deleteRelation(from, relation, desc)
            return
        }
        val pars = mapOf("from" to from.id(), "to" to  to.id(), "optional" to optional)
        val q =
                """
        MATCH (n:${labelFor(from)} { ID : {from}}), (m:${labelFor(to)} { ID : {to})
        MERGE (n)-[r:$relation]->(m)
        ON CREATE SET r.OPTIONAL= {optional}
        RETURN
        """
        tx {
            engine.execute(q, pars)
        }
        subject.onNext(UpdateEvent(desc.entity, from.id(), relation, to.id(), old?.id()))
    }


    override fun <V : Hashable> createRelation(fromKind: String, from: U, toKind: String, to: V, relation: String, optional: Boolean) {
        val pars = mapOf("from" to from, "to" to  to, "optional" to optional)
        val q =
                """
        MATCH (n:${fromKind}), (m:${toKind})
        WHERE n.ID = {from} AND m.ID = {to}
        CREATE UNIQUE (n)-[r:$relation {OPTIONAL: {optional}}]->(m)
        RETURN r
        """
        tx {
            engine.execute(q, pars)
        }
        subject.onNext(UpdateEvent(fromKind, from, relation, to, null))
    }

    override fun <V : Hashable> findRelation(fromKind: String, from: U, toKind: String, relation: String): V? {
        val id = from
        val pars = mapOf("from" to id)
        val q =
                """
        MATCH (n:${fromKind}),(m:${toKind}),(n)-[r:$relation]->(m)
        WHERE n.ID = {from}
        RETURN m.ID as ID
        """
        return tx {
            val executionResult = engine.execute(q, pars)
            val resourceIterator = executionResult?.columnAs<V>("ID")
            var res: V? = null
            if (resourceIterator != null) resourceIterator.with<Unit> {
                if (resourceIterator.hasNext()) res = resourceIterator.next() else null
            }
            res
        }

    }


    override fun <A : LivingElement<U>> deleteRelation(from: A, relation: String, desc: DomainObjectDescriptor) {
        val pars = mapOf("from" to from.id())
        val q =
                """
        MATCH (n:${labelFor(from)} { ID : {from}})-[r:$relation {OPTIONAL = false}]->(to))]
        WHERE HAS(to.ID)
        DELETE r
        RETURN to.ID as ID
        """
        var del: Hashable? = null
        tx {
            val res = engine.execute(q, pars)
            del = res?.columnAs<Hashable>("ID")?.iterator()?.take(1) as Hashable?
        }
        subject.onNext(UpdateEvent(desc.entity, from.id(), relation, null, del))

    }


    override fun <A : LivingElement<U>, B : LivingElement<V>, V : Hashable> findRelations(from: A, to: Class<B>, relation: String, desc: DomainObjectDescriptor): Observable<V> {
        val pars = mapOf("from" to from.id())
        val q =
                """
        MATCH (n:${labelFor(from)} { ID : {from}})-[r:$relation]->(m:${labelForClass(to as Class<LivingElement<*>>)})
        RETURN m.ID as ID
        """
        return tx {
            val executionResult = engine.execute(q, pars)
            val resourceIterator = executionResult?.columnAs<V>("ID")!!
            Observable.from(resourceIterator.toList())!!
        }
    }


    override fun <A : LivingElement<U>, B : LivingElement<V>, V : Hashable> addRelation(from: A, to: B, relation: String, desc: DomainObjectDescriptor) {
        val pars = mapOf("from" to from.id(), "to" to  to.id(), "optional" to true)
        val q =
                """
        MATCH (n:${labelFor(from)} { ID : {from}}), (m:${labelFor(to)} { ID : {to})
        MERGE (n)-[r:$relation]->(m)
        ON CREATE SET r.OPTIONAL= {optional}
        RETURN r
        """
        tx {
            engine.execute(q, pars)
        }
    }


    override fun <A : LivingElement<U>, B : LivingElement<V>, V : Hashable> removeRelation(from: A, to: B, relation: String, desc: DomainObjectDescriptor) {
        val pars = mapOf("from" to from.id(), "to" to to.id())
        val q =
                """
        MATCH (n:${labelFor(from)} { ID : {from}})-[r:$relation {OPTIONAL=true}]->(m:${labelForClass(to as Class<LivingElement<*>>)}{ID:{to}})
        RETURN m.ID as ID
        """
        tx {
            engine.execute(q, pars)
        }
    }


    override fun  schema(cls: Class<*>, desc: DomainObjectDescriptor) {
        tx {
            val i = db.db.index()
            i?.nodeIndexNames()?.forEach {
                log.info(it)
            }
        }
        desc.indices.forEach {

            try {
                tx {
                    if (!(db.db.index()?.existsForNodes(":${desc.entity}($it)")?:false))
                        engine.execute(
                                """
                            CREATE INDEX ON :${desc.entity}($it)
                            """
                        )
                }
            } catch(e: CypherExecutionException) {
                if (e.getCause() is AlreadyIndexedException) {
                    log.warn(e.getMessage())
                }
            }
        }
        desc.uniques.forEach {
            val label = DynamicLabel.label(desc.entity)
            tx {
                if (!(db.db.schema()!!.getConstraints(label)?.iterator()?.hasNext()?:false))
                    db.db.schema()!!.constraintFor(label)!!.on(it)!!.unique()!!.create()
            }
        }

    }
}

class Neo4jQuery(val q: String, val params: Map<String, Any>)

class Neo4jFilterFactory {
    fun<T : LivingElement<U>, U : Hashable> convert(f: ElementFilter<T, U>,
                                                    orderBy: Array<SortKey>, offset: Int, limit: Int): Neo4jQuery {
        val pars: MutableMap<String, Any> = HashMap()
        pars["skip"] = offset
        pars["limit"] = limit
        val skip = if (offset > 0) " SKIP {skip}" else ""
        val lim = if (limit > 0) " LIMIT {limit}" else ""
        val q = """
        MATCH (n:${f.kind}) WHERE ${clause(f, pars)}
        RETURN n.ID as ID
        ${createOrderBy(orderBy)}
        ${skip} ${lim}
        """

        log.info("convert: $f -> $q")
        return Neo4jQuery(q, pars)
    }

    private fun createOrderBy(orderBy: Array<SortKey>): String {
        val sb = StringBuilder()
        orderBy.forEach {
            if (sb.length > 0) sb.append(", ")
            sb.append("n.").append(it.property)
            if (it.direction == SortDirection.DESC)
                sb.append(" ").append(it.direction)
        }
        if (sb.length > 0)
            return "ORDER BY ${sb}"
        return ""
    }


    fun<T : LivingElement<U>, U : Hashable> clause(f: ElementFilter<T, U>, pars: MutableMap<String, Any>): String {
        return when(f) {
            is CombinationFilter -> combine(f, pars)
            is PropertyFilter<T, U, *> -> prop(f, pars)
            else -> "1=1"
        }
    }

    fun<T : LivingElement<U>, U : Hashable> prop(f: PropertyFilter<T, U, *>, pars: MutableMap<String, Any> = HashMap()): String {
        val pn = "p${pars.size + 1}"
        log.info("prop: ${pn} ${if (f.property == "id") "ID" else f.property} ${f.relation} ${f.value}")

        pars.put(pn, f.value)
        log.info("$pars")
        //pars[pn] = f.value
        return "(n.${if (f.property == "id") "ID" else f.property} ${op(f.relation)} { $pn })"
    }

    fun<T : LivingElement<U>, U : Hashable> combine(f: CombinationFilter<T, U>, pars: MutableMap<String, Any> = HashMap()): String {
        val op = op(f.relation)
        val sb = StringBuilder()
        f.combination.forEach {
            if (sb.length() > 0) {
                sb.append(" $op ")
                sb.append("(").append(clause(it, pars)).append(")")
            }
        }
        return sb.toString()
    }

    fun op(rel: FilterRelations): String {
        when(rel) {
            FilterRelations.AND -> return "AND"
            FilterRelations.OR -> return "OR"
            FilterRelations.NOT -> return "NOT"
            FilterRelations.EQ -> return "="
            FilterRelations.NEQ -> return "<>"
            FilterRelations.LT -> return "<"
            FilterRelations.GT -> return ">"
            FilterRelations.LTE -> return "<="
            FilterRelations.GTE -> return ">="
            FilterRelations.LIKE -> return "=~"
            FilterRelations.NOTLIKE -> return "<>~"
            else -> {
                log.error("unknown: $rel")
                throw IllegalStateException()
            }
        }
    }
}

public fun Node.dump(): String {
    val sb = StringBuilder()
    sb.append("Node: ").append(getId()).append(":\n")
    getPropertyKeys()?.forEach {
        sb.append(it).append(": ").append(getProperty(it)).append("\n")
    }
    getRelationships()?.forEach {
        sb.append(it.getType()?.name()).append(": ").append(it.getStartNode()?.getId()).append(" --> ").append(it.getEndNode()?.getId())
    }
    return sb.toString()
}


abstract class Neo4jDomainObject(val oid: Hashable, val store: Neo4jDatastore<*>, protected val kind: String, private val node: Node) : DomainObject {
    private val log = LoggerFactory.getLogger(javaClass<Neo4jDomainObject>())!!
    protected fun<T> prop(name: String): T? = store.tx { node().getProperty(if (name == "id") "ID" else name) as T? }
    protected fun<T> prop(name: String, value: T?): Unit = store.tx { node().setProperty((if (name == "id") "ID" else name), value) }

    protected inline fun node(): Node = node

    public override fun get(p: String): Any? = prop(p)
    public override fun set(p: String, value: Any?): Unit = prop(p, value)
}


fun AutoCloseable.use(run: AutoCloseable.() -> Unit) {
    try {
        run()
    } finally {
        close()
    }
}


fun main(args: Array<String>) {
    val g = Neo4jGenerator(File("../testdomain/target/classes"), true, File("../testdomain/src/main/kotlin", "domain.kt"))

}
