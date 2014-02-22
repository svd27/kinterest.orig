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
import java.util.Date
import java.text.SimpleDateFormat
import ch.passenger.kinterest.Universe
import ch.passenger.kinterest.DomainPropertyDescriptor
import ch.passenger.kinterest.RelationFilter
import rx.observables.ConnectableObservable
import ch.passenger.kinterest.Galaxy

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

class Neo4jDatastore<T:Event<U>, U:Comparable<U>>(val db: Neo4jDbWrapper) : DataStore<Event<U>, U> {
    private val log = LoggerFactory.getLogger(javaClass<Neo4jDatastore<T,U>>())!!;
    private val subject: PublishSubject<Event<U>> = PublishSubject.create()!!;
    private val engine = db.engine;

    {
        val thandler = object : TransactionEventHandler.Adapter<Node>() {
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
                        if (nkind != null) {
                            val d = Universe.descriptor(nkind)
                            val pd = d?.descriptors?.get(it.key())
                            if (d is DomainObjectDescriptor && pd is DomainPropertyDescriptor) {
                                val value = pd.fromDataStore(it.value())
                                log.info("${it.previouslyCommitedValue()} -> $value")
                                if (value != it.previouslyCommitedValue())
                                    subject.onNext(UpdateEvent(nkind, it.entity()?.getProperty("ID") as U,
                                            it.key()!!, value, it.previouslyCommitedValue()))
                            }
                        }
                    }
                }
                data.removedNodeProperties()?.forEach {
                    if (it?.entity()?.hasProperty("ID")?:false && it?.entity()?.hasProperty("KIND")?:false) {
                        val nkind = it.entity()?.getProperty("KIND")?.toString()
                        if (nkind != null && it.previouslyCommitedValue() != null)
                            subject.onNext(UpdateEvent(nkind, it.entity()?.getProperty("ID") as U,
                                    it.key()!!, null, it.previouslyCommitedValue()))
                    }
                }
                /*
                data.createdRelationships()?.forEach {
                    val from = it.getStartNode()!!
                    if (from.hasProperty("ID") && from.hasProperty("KIND")) {
                        val nkind = from?.getProperty("KIND")?.toString()
                        val toid = it.getEndNode()?.getProperty("ID")
                        if (nkind != null && toid!=null)
                            subject.onNext(UpdateEvent(nkind, from?.getProperty("ID") as U, it.getType()?.name()!!, toid, null))
                    }
                }
                data.deletedRelationships()?.forEach {
                    val from = it.getStartNode()!!
                    if (from.hasProperty("ID") && from.hasProperty("KIND")) {
                        val nkind = from?.getProperty("KIND")?.toString()
                        val toid = it.getEndNode()?.getProperty("ID")
                        if (nkind != null && toid!=null)
                            subject.onNext(UpdateEvent(nkind, from?.getProperty("ID") as U, it.getType()?.name()!!, null, toid))
                    }
                }
                */

            }
        }
        //db.db.registerTransactionEventHandler(thandler)
    }


    override fun getValue(id: U, kind: String, p: String): Any? {
        return tx {
            val n = node(id, kind)
            if(n==null || !n.hasProperty(p)) null
            else n.getProperty(p)
        }
    }

    override fun setValue(id: U, kind: String, p: String, v: Any?) {
        tx {
            val n = node(id, kind)
            if(n!=null) {
              val old : Any? = if(n.hasProperty(p)) n.getProperty(p) else null
              if(v!=null) n.setProperty(p, v) else n.removeProperty(p)
              subject.onNext(UpdateEvent(kind, id, p, v, old))
            }
        }
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
                    log.error("q: ${q.q} ${q.params}")
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

    override val observable: Observable<Event<U>> get() = subject.observeOn(ExecutorScheduler(exec))!!;

    {
        val obs = observable
        obs.doOnEach {
            log.info("produce $it")
        }
        obs.doOnError { log.error("error on datastore", it); it?.printStackTrace() }
        obs.doOnCompleted {
            log.warn("Observable in store stopping")
        }
        if(obs is ConnectableObservable<Event<U>>) {
            obs.connect();
        }

    }

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
        if (l.size > 1)
            throw IllegalStateException()

        return if (l.size == 1) l[0] else null
    }


    override fun create(id: U, values: Map<String, Any?>, descriptor: DomainObjectDescriptor) {
        val um: MutableMap<String, Any?> = HashMap()
        values.entrySet().filter { descriptor.uniques.containsItem(it.key) }.map { it.key to it.value }.forEach { um.putAll(it) }
        val setter = values.entrySet().filter { !descriptor.uniques.containsItem(it.key) }.map { it.key }
        val kind = descriptor.entity
        tx {
            val rels = values.keySet().filter { descriptor.descriptors[it]!!.relation }

            val inits = values.filter { !rels.contains(it.first) }.map { "${it.getKey()}: {${it.getKey()}}" }.reduce { a, b -> "$a, $b" }
            val q: String? = """
            MERGE (n:$kind {ID: {id}, KIND: "${descriptor.entity}"${if (inits.length > 0) ", " else ""} $inits})
            RETURN n
            """;
            val m: MutableMap<String, Any> = HashMap()
            values.entrySet().forEach {
                val v = descriptor.descriptors[it.key]!!.toDataStore(it.value)
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
            rels.forEach {
                //val pd = descriptor.descriptors[it]!!
                setRelation(id, values[it] as Comparable<Any>, null, it, descriptor.nullable(it), descriptor)
            }
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

    override public fun<T> atomic(work: ()->T) : T {
        return tx({work()})
    }

    val seqlock = java.util.concurrent.Semaphore(1)

    public fun nextSequence(kind: String): Long {
        seqlock.acquire()
        try {
            return tx {
                val merge = """
                MERGE (n:${kind}Sequence)
                ON CREATE SET n.seq = 0
                ON MATCH SET n.seq = n.seq+1
                RETURN n.seq as SEQ
                """
                val res: ExecutionResult = engine.execute(merge, mapOf())!!
                val resourceIterator = res.columnAs<Long>("SEQ")!!
                if (!resourceIterator.hasNext()) throw IllegalStateException()
                val id = resourceIterator.next()
                resourceIterator.close()
                id
            }
        } finally {
            seqlock.release()
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


    override fun <A : LivingElement<U>, B : LivingElement<V>, V : Comparable<V>> setRelation(from: A, to: B?, old: B?, relation: String, optional: Boolean, desc: DomainObjectDescriptor) {
        setRelation(from.id(), to?.id(), old?.id(), relation, optional, desc)
    }

    override fun <V : Comparable<V>> setRelation(from: U, to: V?, old: V?, relation: String, optional: Boolean, desc: DomainObjectDescriptor) {
        if(from==to) return

        tx {
            deleteRelation(from, relation, desc)
            if (to!=null) {
                val dpd = desc.descriptors[relation]!!
                val tot = labelForClass(dpd.classOf as Class<LivingElement<*>>)
                val fk : String = desc.entity
                val pars : Map<String,Any> = mapOf<String,Any>("from" to from, "to" to  to, "optional" to optional)
                val q =
                        """
            MATCH (n:${fk}), (m:${tot})
            WHERE n.ID = {from} and m.ID = {to}
            CREATE UNIQUE (n)-[r:$relation {OPTIONAL: {optional}}]->(m)
            RETURN r
            """
                log.info("executing\n$q\n$pars")
                engine.execute(q, pars)
            }
        }
        if(to!=old)
        subject.onNext(UpdateEvent(desc.entity, from, relation, to, old))
    }


    override fun <V : Comparable<V>> createRelation(fromKind: String, from: U, toKind: String, to: V, relation: String, optional: Boolean) {
        val pars : Map<String, Any> = mapOf("from" to from, "to" to  to, "optional" to optional)
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
        if(to!=null)
        subject.onNext(UpdateEvent(fromKind, from, relation, to, null))
    }

    override fun <V : Comparable<V>> findRelation(fromKind: String, from: U, toKind: String, relation: String): V? {
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
        deleteRelation(from.id(), relation, desc)
    }

    override fun deleteRelation(from: U, relation: String, desc: DomainObjectDescriptor) {
        val pars = mapOf("from" to from)
        val q =
                """
        MATCH (n:${desc.entity})-[r:$relation]->(to)
        WHERE n.ID = {from} AND HAS(to.ID)
        DELETE r
        RETURN to.ID as ID
        """
        var del: Comparable<Any>? = null
        tx {
            val res = engine.execute(q, pars)

            val resourceIterator = res?.columnAs<Comparable<Any>>("ID")
            if(resourceIterator!=null && resourceIterator.hasNext())
              del = resourceIterator?.iterator()?.take(1)?.next() as Comparable<Any>?
        }
        if(del!=null)
          subject.onNext(UpdateEvent(desc.entity, from, relation, null, del))
    }



    override fun <V:Comparable<V>> findRelations(from: U, relation: String, desc: DomainObjectDescriptor): Observable<V> {
        val pars = mapOf("from" to from)
        val q =
                """
        MATCH (n:${desc.entity})-[r:$relation]->(m:${desc.descriptors[relation]?.targetEntity})
        WHERE n.ID = {from}
        RETURN m.ID as ID
        ORDER BY m.ID
        """
        return tx {
            val executionResult = engine.execute(q, pars)
            val resourceIterator = executionResult?.columnAs<V>("ID")!!
            Observable.from(resourceIterator.toList())!!
        }
    }


    override fun <V : Comparable<V>> findNthRelations(from: U, relation: String, nth: Int, desc: DomainObjectDescriptor): Observable<V> {
        val pars : Map<String,Any> = mapOf("from" to from, "nth" to nth)
        val q =
                """
        MATCH (n:${desc.entity})-[r:$relation]->(m:${desc.descriptors[relation]?.targetEntity})
        WHERE n.ID = {from}
        RETURN m.ID as ID
        ORDER BY m.ID
        SKIP {nth} LIMIT 1
        """
        return tx {
            val executionResult = engine.execute(q, pars)
            val resourceIterator = executionResult?.columnAs<V>("ID")!!
            Observable.from(resourceIterator.toList())!!
        }
    }

    override fun <V:Comparable<V>> countRelations(from: U, relation: String, desc: DomainObjectDescriptor): Observable<Int> {
        val pars = mapOf("from" to from)
        val q =
                """
        MATCH (n:${desc.entity})-[r:$relation]->(m:${desc.descriptors[relation]?.targetEntity})
        WHERE n.ID = {from}
        RETURN COUNT(m.ID) as N
        """
        return tx {
            val executionResult = engine.execute(q, pars)
            val resourceIterator = executionResult?.columnAs<Long>("N")!!
            Observable.from(resourceIterator.toList())!!.map {it?.toInt()}!!
        }
    }


    override fun <A : LivingElement<U>, B : LivingElement<V>, V : Comparable<V>> addRelation(from: A, to: B, relation: String, desc: DomainObjectDescriptor) {
        val pars :Map<String,Any> = mapOf("from" to from.id(), "to" to  to.id(), "optional" to true)
        val q =
                """
        MATCH (n:${from.descriptor().entity}), (m:${to.descriptor().entity})
        WHERE n.ID = {from} AND m.ID = {to}
        CREATE UNIQUE (n)-[r:${relation} {OPTIONAL: true}]->(m)
        RETURN r
        """
        tx {
            log.info("ADD RELATION ${q}: $pars")
            engine.execute(q, pars)
        }
        subject.onNext(UpdateEvent(desc.entity, from.id(), relation, to.id(), null))
    }


    override fun <V : Comparable<V>> removeRelation(from: U, to: V, relation: String, desc: DomainObjectDescriptor) {
        val pars = mapOf("from" to from, "to" to to)
        val q =
                """
        MATCH (n:${desc.entity})-[r:$relation]->(m:${desc.descriptors[relation]!!.targetEntity})
        WHERE n.ID = {from} and m.ID = {to} and r.OPTIONAL = true
        DELETE r
        RETURN m.ID as ID
        """
        tx {
            log.info("REMOVE RELATION ${q}: $pars")
            engine.execute(q, pars)
        }
        subject.onNext(UpdateEvent(desc.entity, from, relation, null, to))
    }


    override fun  schema(cls: Class<*>, desc: DomainObjectDescriptor) {
        log.info("SCHEMA ${desc.entity}")
        tx {
            val i = db.db.index()
            i?.nodeIndexNames()?.forEach {
                log.info(it)
            }
        }
        try {
            tx {
                if (!(db.db.index()?.existsForNodes(":${desc.entity}(KIND)")?:false))
                {
                    val q = """
                                CREATE INDEX ON :${desc.entity}(KIND)
                                """
                    log.info("$q")
                    engine.execute(
                            q
                    )
                }
            }
        } catch(e: Exception) {
            if (e.getCause() is AlreadyIndexedException) {
                log.warn(e.getMessage())
            } else throw e
        }
        desc.indices.forEach {

            try {
                tx {
                    if (!(db.db.index()?.existsForNodes(":${desc.entity}($it)")?:false))
                    {
                        val q = """
                            CREATE INDEX ON :${desc.entity}($it)
                            """
                        log.info(q)
                        engine.execute(
                                q
                        )
                    }
                }
            } catch(e: CypherExecutionException) {
                if (e.getCause() is AlreadyIndexedException) {
                    log.warn(e.getMessage())
                }
                else throw e
            }
        }
        val label = DynamicLabel.label(desc.entity)
        tx {
            desc.uniques.forEach {
                val q = """
                            CREATE CONSTRAINT ON (n:${desc.entity}) ASSERT n.${it} IS UNIQUE
                            """
                log.info(q)
                engine.execute(
                        q
                )
            }
        }
        tx {
            val q = """
                            CREATE CONSTRAINT ON (n:${desc.entity}) ASSERT n.ID IS UNIQUE
                            """
            log.info(q)
            engine.execute(
                    q
            )
        }
    }
 }

class Neo4jQuery(val q: String, val params: Map<String, Any>)

class Neo4jFilterFactory {
    fun<T : LivingElement<U>, U : Comparable<U>> convert(f: ElementFilter<T, U>,
                                                    orderBy: Array<SortKey>, offset: Int, limit: Int): Neo4jQuery {
        val pars: MutableMap<String, Any> = HashMap()
        pars["skip"] = offset
        pars["limit"] = limit
        val matches : MutableMap<String,String> = HashMap()
        matches["n"] = "(n:${f.kind})"
        val ac = clause<T,U,Comparable<Any?>>("n", f, pars, matches)
        val skip = " SKIP {skip}"
        val lim = if (limit > 0) " LIMIT {limit}" else ""
        val q = """
        MATCH ${matches.values().makeString(", ")} WHERE ${ac}
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


    fun<T : LivingElement<U>, U:Comparable<U>,V:Comparable<V>> clause(on:String, f: ElementFilter<T, U>, pars: MutableMap<String, Any>, matches:MutableMap<String,String>): String {
        return when(f) {
            is CombinationFilter -> combine(on, f, pars, matches)
            is PropertyFilter<T, U, *> -> prop(on, f, pars, matches)
            is RelationFilter<T,U> -> relation(on, f, pars, matches)
            else -> "1=1"
        }
    }

    fun<T : LivingElement<U>, U : Comparable<U>> prop(on:String, f: PropertyFilter<T, U, *>, pars: MutableMap<String, Any> = HashMap(), matches:MutableMap<String,String>): String {
        val pn = "p${pars.size + 1}"
        log.info("prop: ${pn} ${if (f.property == "id") "ID" else f.property} ${f.relation} ${f.value}")
        matches[on] = "($on:${f.kind})"

        //TODO: HACK, we need a strategic solution for this
        if(f.value.javaClass.isEnum()) {
            pars.put(pn, (f.value as Enum<*>).name())
        } else if(f.value is Date) {
            pars.put(pn, convertDate(f.value))
        }
        else pars.put(pn, f.value)
        log.info("$pars")
        return "($on.${if (f.property == "id") "ID" else f.property} ${op(f.relation)} { $pn })"
    }

    private val sdf = SimpleDateFormat("yyyyMMddHHmmssSSS")

    fun convertDate(d:Date) : Long {
        return java.lang.Long.parseLong(sdf.format(d))
    }

    fun<T : LivingElement<U>, U : Comparable<U>> relation(on:String, f: RelationFilter<T, U>, pars: MutableMap<String, Any> = HashMap(), matches:MutableMap<String,String>): String {
        val par = "$on${matches.size()}"
        matches[par] = "($par:${f.f.kind})"
        val rpar = "r${matches.size()}"
        if(f.relation==FilterRelations.FROM) {
            matches[rpar] = "($on)<-[:${f.property}]-($par)"
            return " ${clause<LivingElement<Comparable<Any>>,Comparable<Any>,Comparable<Any>>(par, f.f as ElementFilter<LivingElement<Comparable<Any>>,Comparable<Any>>, pars, matches) }"
        } else {
            matches[rpar] = "($on)-[:${f.property}]->($par)"
            return " ${clause<LivingElement<Comparable<Any>>,Comparable<Any>,Comparable<Any>>(par, f.f as ElementFilter<LivingElement<Comparable<Any>>,Comparable<Any>>, pars, matches) }"
        }
    }

    fun<T : LivingElement<U>, U : Comparable<U>> combine(on:String, f: CombinationFilter<T, U>, pars: MutableMap<String, Any> = HashMap(), matches:MutableMap<String,String>): String {
        val op = op(f.relation)
        val sb = StringBuilder()
        f.combination.forEach {
            if (sb.length() > 0) {
                sb.append(" $op ")
            }
            sb.append("(").append(clause<T,U,Comparable<Any>>(on, it, pars, matches)).append(")")
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


abstract class Neo4jDomainObject<T:Comparable<T>>(val oid: T, val store: Neo4jDatastore<Event<T>,T>, protected val kind: String, private val node: Node, val descriptor:DomainObjectDescriptor) : DomainObject {
    private val log = LoggerFactory.getLogger(javaClass<Neo4jDomainObject<T>>())!!
    protected fun<U> prop(name:String, pd:DomainPropertyDescriptor): U? = store.tx {
        val n = if (name == "id") "ID" else name
        if(node().hasProperty(n)) {
            pd.fromDataStore(node().getProperty(n)) as U?
        }
        else null
    }

    protected fun<U> prop(name: String, pd:DomainPropertyDescriptor, value: U?): Unit = store.tx {
        node().setProperty((if (name == "id") "ID" else name), pd.toDataStore(value))
    }

    protected inline fun node(): Node = node

    public override fun get(p: String, pd:DomainPropertyDescriptor): Any? = prop(p, pd)
    public override fun set(p: String, pd:DomainPropertyDescriptor, value: Any?): Unit = prop(p, pd, value)
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
