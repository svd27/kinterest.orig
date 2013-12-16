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
                if(data==null) return
                data.createdNodes()?.forEach {
                    log.info("created node ${it.getId()}")
                    val nkind = it.getProperty("KIND")?.toString()
                    if(nkind!=null)
                    subject.onNext(CreateEvent(nkind, it.getProperty("ID") as U))
                }
                data.deletedNodes()?.forEach {
                    log.info("deleted node ${it.getId()}")
                    val nkind = it.getProperty("KIND")?.toString()
                    if(nkind!=null)
                    subject.onNext(DeleteEvent(nkind, it.getProperty("ID") as U))
                }
                data.assignedNodeProperties()?.forEach {
                    log.info("prop change: ${it.entity()?.getId()}.${it.key()}: ${it.previouslyCommitedValue()} -> ${it.value()}")
                    val nkind = it.entity()?.getProperty("KIND")?.toString()
                    if(nkind!=null)
                    subject.onNext(UpdateEvent(nkind, it.entity()?.getProperty("ID") as U,
                            it.key()!!, it.value(), it.previouslyCommitedValue()))
                }
                data.removedNodeProperties()?.forEach {
                    val nkind = it.entity()?.getProperty("KIND")?.toString()
                    if(nkind!=null)
                    subject.onNext(UpdateEvent(nkind, it.entity()?.getProperty("ID") as U,
                            it.key()!!, null, it.previouslyCommitedValue()))
                }
                data.createdRelationships()?.forEach {
                    val from = it.getStartNode()
                    val nkind = from?.getProperty("KIND")?.toString()
                    val toid = it.getEndNode()?.getProperty("ID")
                    if(nkind!=null)
                        subject.onNext(UpdateEvent(nkind, from?.getProperty("ID") as U, it.getType()?.name()!!, toid, null))
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

    override val observable: Observable<Event<U>> get() = subject

    public fun  node(id: U, kind:String): Node? {
        val m = mapOf("oid" to id)
        val q = """
        MATCH (n:$kind) WHERE n.ID = {oid}
        RETURN n
        """
        val res = engine.execute(q, m)!!
        val l = res.columnAs<Node>("n")!!.toList()
        log.info { "get result: ${l}" }
        l.forEach { log.info(it.dump()) }
        if(l.size>1) throw IllegalStateException()

        return if(l.size==1) l[0] else null
    }

    public fun create(id: U, kind:String, init: (n: Node) -> Unit): Node {
        return tx {
            val m = mapOf("oid" to id)
            val q = """
            MERGE (n:$kind {ID : {oid}})
            RETURN n
            """
            val res = engine.execute(q, m)!!
            val l = res.columnAs<Node>("n")!!.toList()
            log.info { "create result: ${l}" }
            l.forEach { log.info(it.dump()) }
            if(l.size>1) throw IllegalStateException()
            init(l[0])
            //log.info ("created: " + node.dump() )
            success()
            l[0]
        }
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

    public fun nextSequence(kind:String): Long {
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

    override fun <A : LivingElement<U>, B : LivingElement<V>, U : Hashable, V : Hashable> setRelation(from: A, to: B?, relation: String, optional:Boolean) {
        if (to == null) {
            deleteRelation(from, relation)
        }
        val nnto = to!!
        val pars = mapOf("from" to from.id(), "to" to  to.id(), "optional" to optional)
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


    override fun <U : Hashable, V : Hashable> createRelation(fromKind: String, from: U, toKind: String, to: V, relation: String, optional: Boolean) {
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
    }

    override fun <U : Hashable, V : Hashable> findRelation(fromKind: String, from: U, toKind: String, relation: String): V? {
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
            var res : V? = null
            if(resourceIterator!=null) resourceIterator.with<Unit> {
                if (resourceIterator.hasNext()) res=resourceIterator.next() else null
            }
            res
        }

    }


    override fun <A : LivingElement<U>, U : Hashable> deleteRelation(from: A, relation: String) {
        val pars = mapOf("from" to from.id())
        val q =
                """
        MATCH (n:${labelFor(from)} { ID : {from}})-[r:$relation {OPTIONAL = false}]->())]
        RETURN r
        """
        tx {
            engine.execute(q, pars)
        }
    }


    override fun <A : LivingElement<U>, B : LivingElement<V>, U : Hashable, V : Hashable> findRelations(from: A, to: Class<B>, relation: String): Observable<V> {
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


    override fun <A : LivingElement<U>, B : LivingElement<V>, U : Hashable, V : Hashable> addRelation(from: A, to: B, relation: String) {
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


    override fun <A : LivingElement<U>, B : LivingElement<V>, U : Hashable, V : Hashable> removeRelation(from: A, to: B, relation: String) {
        val pars = mapOf("from" to from.id(), "to" to to.id() )
        val q =
                """
        MATCH (n:${labelFor(from)} { ID : {from}})-[r:$relation {OPTIONAL=true}]->(m:${labelForClass(to as Class<LivingElement<*>>)}{ID:{to}})
        RETURN m.ID as ID
        """
        tx {
            engine.execute(q, pars)
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
        log.info("prop: ${pn} ${f.property} ${f.relation} ${f.value}")

        pars.put(pn, f.value)
        log.info("$pars")
        //pars[pn] = f.value
        return "(n.${f.property} ${op(f.relation)} { $pn })"
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
    protected fun<T> prop(name: String): T? = store.tx { node().getProperty(name) as T? }
    protected fun<T> prop(name: String, value: T?): Unit = store.tx { node().setProperty(name, value) }

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

class Neo4jGenerator(val file: File, val recurse: Boolean, val target: File) {
    private val log = LoggerFactory.getLogger(javaClass<Neo4jGenerator>())!!
    private val pool: ClassPool = ClassPool.getDefault()!!;
    val domainBuffer = StringBuilder();

    {
        val fw: Writer = FileWriter(target)
        log.info("Target: ${target.getAbsolutePath()}")
        loadClasses(file)
        fw.use {
            fw.write("//Generated: ${DateTime()}")
            generate(file, recurse, fw)
            val appendix =
                    """
        public fun boostrapDomain(db:ch.passenger.kinterest.neo4j.Neo4jDbWrapper) {
        $domainBuffer
        }
        """
            fw.append("\n").append(appendix)
            fw.flush()
        }
        log.info("$fw")

    }

    fun loadClasses(from:File) {
        log.info("load classes")
        if (from.isFile() && from.getAbsolutePath().endsWith(".class")) {
            val ctClass = pool.makeClass(FileInputStream(from))!!
            log.info("loaded ${ctClass}")
        } else if (from.isDirectory() && recurse) {
            from.listFiles()?.forEach { loadClasses(it) }
        }
    }

    fun generate(f: File, recurse: Boolean, target: Writer) {
        log.info("generate $f $recurse ${f.isFile()} ${f.getName()} ${f.getName().endsWith(".class")}")
        if (f.isFile() && f.getAbsolutePath().endsWith(".class")) {
            val ctClass = pool.makeClass(FileInputStream(f))!!
            log.info("ctClass: ${ctClass.getName()}")
            ctClass.getAnnotations()!!.forEach { log.info("$it") }
            val entity = ctClass.getAnnotation(javaClass<Entity>())
            if (entity != null) {
                target.append(output(ctClass))
            }

        } else if (f.isDirectory() && recurse) {
            f.listFiles()?.forEach { generate(it, recurse, target) }
        }

    }


    fun output(cls: CtClass): String {
        val mths = methods(cls)
        var id: Prop? = null
        val mandatory: MutableList<Prop> = ArrayList()
        mths.values().forEach {
            log.info("checking prop: $it")
            if (it.id) {
                id = it
            }
            else if (it.ro && it.defval() == null) {
                log.info("mandatory: $it")
                val b = mandatory.add(it)
            }
        }
        val cn = if (cls.getName()!!.indexOf('.') > 0) cls.getName()!!.substring(cls.getName()!!.lastIndexOf('.') + 1)
        else cls.getName()!!

        val mandPars = StringBuilder()
        val crtInit = StringBuilder()
        val body = StringBuilder()
        mths.values().forEach {
            if (!it.id && !it.onetoone && !it.ontomany) {
                body.append("\noverride ")
                if (it.ro) body.append("val ") else body.append("var ")
                body.append(it.name).append(" : ").append(it.kind)
                if (it.nullable) body.append("?")
                body.append("\nget() = prop(\"${it.name}\")")
                if (!it.nullable) body.append("!!")
                if (!it.ro) {
                    body.append("\nset(v) = prop(\"${it.name}\", v)")
                }
                if (it.ro) {
                    mandPars.append("${it.name} : ${it.kind}${if(it.nullable) '?' else ' '}, ")
                    crtInit.append("\n").append("it.setProperty(\"").append(it.name).append("\",").append(it.name).append(")")
                }
                if (!it.ro) {
                    if (it.defval() == null && !it.nullable) {
                        mandPars.append(it.name).append(" : ").append(it.kind).append(if (it.nullable) "?" else "").append(", ")
                        crtInit.append("\n").append("it.setProperty(\"").append(it.name).append("\",").append(it.name).append(")")
                    } else if (!it.nullable) {
                        crtInit.append("\n").append("it.setProperty(\"").append(it.name).append("\",").append(it.defval()).append(")")
                    }
                }
            }
            if(!it.id && it.onetoone) {
                val entity : Entity = it.ms[0].getReturnType()!!.getAnnotation(javaClass<Entity>())!! as Entity
                body.append("\noverride ")
                if (it.ro) body.append("val ") else body.append("var ")
                body.append(it.name).append(" : ").append(it.kind)
                var nullreturn = "return null"
                if (it.nullable) {
                    body.append("?")
                } else nullreturn = "throw IllegalStateException()"

                body.append("""
                get() {
                 val oid = ${cn}Impl.galaxy.relation(id(), javaClass<${it.kind}>(), "${it.name}")
                 if(oid!=null) return ch.passenger.kinterest.Universe.get(javaClass<${it.kind}>(), oid)
                 $nullreturn
                }""")

                if (!it.ro) {
                    body.append("""
                    set(v) {${cn}Impl.galaxy.setRelation(id(), ${it.name}, "${it.name}", ${it.nullable})}
                    """)
                }
                if (it.ro) {
                    mandPars.append("${it.name} : ${it.kind}${if(it.nullable) '?' else ' '}, ")
                    if(it.nullable)
                        crtInit.append("\nif(${it.name}!=null)")

                    crtInit.append("""
                    ${cn}Impl.galaxy.createRelation(id, "${entity.name()}", ${it.name}.id(), "${it.name}", ${it.nullable})
                    """)
                }
                if (!it.ro) {
                    if (it.defval() == null && !it.nullable) {
                        mandPars.append(it.name).append(" : ").append(it.kind).append(if (it.nullable) "?" else "").append(", ")
                        crtInit.append("""
                        ${cn}Impl.galaxy.createRelation(id, "${entity.name()}", ${it.name}.id(), "${it.name}", ${it.nullable})
                        """)
                    } else if (!it.nullable) {
                        throw IllegalStateException("${it.name} cannot generate")
                    }
                }
            }
            if(!it.id && it.ontomany) {
                if(it.nullable || !it.ro) throw IllegalStateException()
                val many : OneToMany = it.ms[0].getAnnotation(javaClass<OneToMany>())!! as OneToMany

                val target = many.targetEntity()!!
                var ret : Class<*>? = null
                target.getMethods().forEach {
                    if(it.getAnnotation(javaClass<Id>())!=null) {
                        ret = it.getReturnType()
                    }
                }
                var rtype =ret?.getName()
                val trans = mapOf("java.lang.String" to "String", "long" to "Long", "double" to "Double",
                        "java.util.List" to "jet.MutableList");
                if(trans.containsKey(rtype)) rtype = trans[rtype]
                body.append("""
                  override val ${it.name} : ${it.kind}<${target.getName()},${rtype}> =
                  ch.passenger.kinterest.Interest<${target.getName()},${rtype}>("", javaClass<${target.getName()}>())
                """)

            }
        }

        var label = cls.getName()
        val ean = cls.getAnnotation(javaClass<Entity>())
        if (ean is Entity && !(ean.name()?.isEmpty()?:true)) {
            label = ean.name()
        }
        val cimpl = "${cn}Impl"

        domainBuffer.append("boostrap${cn}(db)\n")

        body.append("""
        public fun equals(o :Any?) : Boolean {
        return when(o) {
            is ${cls.getName()} ->  id().equals(o.id())
            else -> false
        }
    }

    public fun hashCode() : Int = id.hashCode()
        """)

        return """
class ${cn}Impl(val id:${id!!.kind}, store:ch.passenger.kinterest.neo4j.Neo4jDatastore<${id!!.kind}>, node:org.neo4j.graphdb.Node) : ch.passenger.kinterest.neo4j.Neo4jDomainObject(id, store, ${cn}Impl.kind,node), ${cls.getName()}, ch.passenger.kinterest.LivingElement<${id!!.kind}> {
  override fun id() : ${id!!.kind} = id
  override protected val subject = subject()
  $body

  class object {
    val kind : String = "${label}"
    val galaxy : ch.passenger.kinterest.Galaxy<${cls.getName()},${id!!.kind}> get() = ch.passenger.kinterest.Universe.galaxy(javaClass<${cls.getName()}>())!!
    fun create(${id!!.name}:${id!!.kind}, ${mandPars} store:ch.passenger.kinterest.neo4j.Neo4jDatastore<${id!!.kind}>) : ${cls.getName()} {
                val node = store.create(${id!!.name}, kind){
                ${crtInit}
                }
                return ${cn}Impl(${id!!.name}, store, node)

        }
        fun get(${id!!.name}:${id!!.kind}, store:ch.passenger.kinterest.neo4j.Neo4jDatastore<${id!!.kind}>) : ${cls.getName()}? {
        return store.tx {
           var res : ${cls.getName()}? = null
            val node = store.node(${id!!.name}, kind)
            if(node!=null) res = ${cn}Impl(${id!!.name}, store, node)
            res
        }
      }
  }
}

class ${cn}Galaxy(val neo4j:ch.passenger.kinterest.neo4j.Neo4jDatastore<${id!!.kind}>) : ch.passenger.kinterest.Galaxy<${cls.getName()},Long>(javaClass<${cls.getName()}>(), neo4j) {
    override fun generateId(): ${id!!.kind} = neo4j.nextSequence(kind) as ${id!!.kind}
    override fun retrieve(id: ${id!!.kind}): ${cls.getName()}? = $cimpl.get(id, neo4j)
    override fun create(values:Map<String,Any?>) : ${cls.getName()} {
        val n = neo4j.create(generateId(), kind) {
            values.entrySet().forEach {
                e ->
                if(e.getKey()!="ID" && e.getKey()!="KIND") {
                    it.setProperty(e.getKey(), e.getValue())
                }
            }
        }
        return $cimpl.get(n.getProperty("ID") as ${id!!.kind}, neo4j)!!
    }
}

public fun boostrap${cn}(db:ch.passenger.kinterest.neo4j.Neo4jDbWrapper) {
    ch.passenger.kinterest.Universe.register(${cn}Galaxy(ch.passenger.kinterest.neo4j.Neo4jDatastore(db)))
}
        """


    }


    inner class Prop(val name: String, val ms: Array<CtMethod>) {
        val trans = mapOf("java.lang.String" to "String", "long" to "Long", "double" to "Double",
                "java.util.List" to "jet.MutableList");
        val ro: Boolean get() = ms.size == 1
        fun defval(): String? {
            val dv = ms[0].getAnnotation(javaClass<DefaultValue>()) as DefaultValue?
            return dv?.value
        }
        val id: Boolean = has(javaClass<Id>())
        val nullable: Boolean = has(javaClass<Nullable>())

        fun has(ann: Class<*>): Boolean {
            if (ms[0].getAnnotation(ann) != null) return true
            if (ms.size > 1 && ms[1].getAnnotation(ann) != null) return true
            return false
        }

        val kind: String get() {
            val rt = ms[0].getReturnType()!!.getName()!!
            if (trans.containsKey(rt)) return trans[rt]!!
            return rt
        }

        val onetoone : Boolean get()  = ms[0].getAnnotation(javaClass<OneToOne>())!=null
        val ontomany : Boolean get()  = ms[0].getAnnotation(javaClass<OneToMany>())!=null

        public fun toString(): String = "$name: id? $id ro? $ro default: ${defval()} null: $nullable"
        public fun dumpAnn(): String {
            val sb = StringBuilder()
            sb.append(ms[0].getName()).append(": ")
            ms[0].getAnnotations()!!.forEach { sb.append(it).append(" ") }
            return sb.toString()
        }
    }

    fun methods(cls: CtClass): Map<String, Prop> {
        val props: MutableMap<String, Prop> = HashMap()
        cls.getMethods()?.forEach {
            if (!Modifier.isStatic(it.getModifiers()) && !Modifier.isPrivate(it.getModifiers())) {
                if (it.getName()!!.startsWith("get")) {
                    if (it.getAnnotation(javaClass<Transient>()) == null) {
                        val capName = it.getName()!!.substring(3)
                        val pn = capName.decapitalize()
                        if (pn != "class") {
                            var setter: CtMethod? = null
                            cls.getMethods()!!.forEach {
                                if (it.getName() == "set${capName}") setter = it
                            }
                            if (setter == null)
                                props[pn] = Prop(pn, array(it))
                            else props[pn] = Prop(pn, array(it, setter!!))
                        }
                    }
                } else if(it.getAnnotation(javaClass<Id>())!=null) {
                    props[it.getName()!!] = Prop(it.getName()!!, array(it))
                }
            }
        }

        return props
    }
}

fun main(args: Array<String>) {
    val g = Neo4jGenerator(File("../testdomain/target/classes"), true, File("../testdomain/src/main/kotlin", "domain.kt"))

}
