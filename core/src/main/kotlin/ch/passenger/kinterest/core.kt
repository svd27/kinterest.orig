package ch.passenger.kinterest

import rx.Observable
import java.util.HashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import rx.Observer
import rx.Observable.OnSubscribeFunc
import rx.Subscription
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.HashSet
import java.lang.reflect.Method
import javax.persistence.Entity
import java.util.WeakHashMap
import kotlin.properties.Delegates
import rx.subjects.PublishSubject
import java.util.Comparator
import javax.persistence.Transient
import javax.persistence.OneToOne
import org.jetbrains.annotations.Nullable
import javax.persistence.Id
import javax.persistence.UniqueConstraint
import ch.passenger.kinterest.annotations.Index
import ch.passenger.kinterest.util.filter
import java.lang.reflect.Modifier
import ch.passenger.kinterest.service.EntityPublisher
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.persistence.OneToMany
import java.util.Date
import java.text.SimpleDateFormat
import ch.passenger.kinterest.util.json.EnumDecoder

/**
 * Created by svd on 11/12/13.
 */
private val log: Logger = LoggerFactory.getLogger("ch.passenger.kinterest.core")!!
//trait Identifiable<T : Hashable>  {
//    public fun id() : T
//}

trait LivingElement<T : Hashable> {
    Id
    public fun id(): T
    public fun consume(evt: UpdateEvent<T, Any?>) {
        subject.onNext(evt)
    }
    protected fun subject(): PublishSubject<UpdateEvent<T, Any?>> = PublishSubject.create<UpdateEvent<T, Any?>>()!!

    protected val subject: PublishSubject<UpdateEvent<T, Any?>> [Transient] get
    val observable: Observable<UpdateEvent<T, Any?>> [Transient] get() = subject
    fun galaxy(): Galaxy<out LivingElement<T>, out T>
    fun descriptor(): DomainObjectDescriptor
}



public enum class EventTypes {
    CREATE ADD DELETE REMOVE UPDATE ORDER INTEREST
}

open class Event<U : Hashable>(public val sourceType: String, public val kind: EventTypes)
open class ElementEvent<U : Hashable>(sourceType: String, public val id: U, kind: EventTypes) : Event<U>(sourceType, kind)
open class InterestEvent<U : Hashable>(val interest: Int, sourceType: String, public val id: U, kind: EventTypes) : Event<U>(sourceType, kind)
class UpdateEvent<U : Hashable, V>(sourceType: String, id: U, public val property: String, public val value: V?, public val old: V?)
: ElementEvent<U>(sourceType, id, EventTypes.UPDATE)
class CreateEvent<U : Hashable>(sourceType: String, id: U)
: ElementEvent<U>(sourceType, id, EventTypes.CREATE)
class AddEvent<U : Hashable>(interest: Int, sourceType: String, id: U)
: InterestEvent<U>(interest, sourceType, id, EventTypes.ADD)
class RemoveEvent<U : Hashable>(interest: Int, sourceType: String, id: U)
: InterestEvent<U>(interest, sourceType, id, EventTypes.REMOVE)
class DeleteEvent<U : Hashable>(sourceType: String, id: U)
: ElementEvent<U>(sourceType, id, EventTypes.DELETE)
class OrderEvent<U : Hashable>(val interest: Int, sourceType: String, val order: Iterable<U>) : Event<U>(sourceType, EventTypes.ORDER)
class InterestConfigEvent<U:Hashable>(sourceType:String, val interest:Int, val size:Int, val offset:Int, val limit:Int, val orderBy:Array<SortKey>) : Event<U>(sourceType, EventTypes.INTEREST)


trait DataStore<T : Event<U>, U : Hashable> {
    val observable: Observable<T>
    fun<T : LivingElement<U>> filter(f: ElementFilter<T, U>, orderBy: Array<SortKey>, offset: Int, limit: Int): Observable<U>
    /**
     * this is called to initialise non-nullable relations when the root has not yet been created
     */
    fun<V : Hashable> createRelation(fromKind: String, from: U, toKind: String, to: V, relation: String, optional: Boolean)
    fun<V : Hashable> findRelation(fromKind: String, from: U, toKind: String, relation: String): V?
    fun<A : LivingElement<U>, B : LivingElement<V>, V : Hashable> setRelation(from: A, to: B?, old: B?, relation: String, optional: Boolean, desc: DomainObjectDescriptor)
    fun<A : LivingElement<U>> deleteRelation(from: A, relation: String, desc: DomainObjectDescriptor)
    fun<A : LivingElement<U>, B : LivingElement<V>, V : Hashable> findRelations(from: A, to: Class<B>, relation: String, desc: DomainObjectDescriptor): Observable<V>
    fun<A : LivingElement<U>, B : LivingElement<V>, V : Hashable> addRelation(from: A, to: B, relation: String, desc: DomainObjectDescriptor)
    fun<A : LivingElement<U>, B : LivingElement<V>, V : Hashable> removeRelation(from: A, to: B, relation: String, desc: DomainObjectDescriptor)
    /**
     * should be called by any galaxy during init to ensure proper schema init (constraints, indices, etc)
     */
    fun schema(cls: Class<*>, desc: DomainObjectDescriptor)
    fun create(id: U, values: Map<String, Any?>, descriptor: DomainObjectDescriptor)
}

trait DomainObject {
    fun get(p: String, pd:DomainPropertyDescriptor): Any?
    fun set(p: String, pd:DomainPropertyDescriptor, value: Any?): Unit
}

class DomainObjectDescriptor(val cls: Class<*>) {
    public val entity: String = cls.entityName();
    public val uniques: Iterable<String> = cls.getMethods().filter {
        it.unique()
    }.map { it.propertyName() }
    public val indices: Iterable<String> = cls.getMethods().filter {
        it.index()
    }.map { it.propertyName() }
    private val getters: MutableMap<String, Method> = HashMap()
    private val setters: MutableMap<String, Method> = HashMap();
    {
        cls.getMethods().filter {
            !Modifier.isStatic(it.getModifiers()) && !Modifier.isPrivate(it.getModifiers())
            && !it.transient() && (it.getName()!!.startsWith("get") || it.getName()!!.startsWith("is")) && it.getReturnType() != javaClass<Void>()
            && it.getParameterTypes()?.size?:1 == 0
        }.forEach {
            getters[it.propertyName()] = it
        }
        cls.getMethods().filter {
            !Modifier.isStatic(it.getModifiers()) && !Modifier.isPrivate(it.getModifiers())
            && !it.transient() && it.getName()!!.startsWith("set") && it.getReturnType() != javaClass<Void>()
        }.forEach {
            setters[it.propertyName()] = it
        }
    }

    public val properties: Iterable<String> = getters.keySet()

    public fun set(target: Any, prop: String, value: Any?) {
        setters[prop]!!.invoke(target, value)
    }

    public fun get(target: Any, prop: String): Any? {
        try {
            return getters[prop]!!invoke(target)
        } catch(e: Exception) {
            log.info("error getting prop $prop on $target ${getters[prop]}")
            throw e
        }
    }

    public val descriptors: Map<String, DomainPropertyDescriptor>

    {
        val m: MutableMap<String, DomainPropertyDescriptor> = HashMap()
        getters.entrySet().forEach {
            val setter = setters[it.key]
            m[it.key] = DomainPropertyDescriptor(it.key, it.value, setter)
        }
        descriptors = m
    }
}

class DomainPropertyDescriptor(val property: String, val getter: Method, val setter: Method?) {
    val classOf: Class<*> = getter.getReturnType()!!
    val relation: Boolean = getter.relation()
    val enum : Boolean = getter.getReturnType()!!.isEnum()

    val linkType: Class<*>?;
    {
        if (relation) {
            log.info("descriptor for: $classOf -> $relation")
            linkType = classOf.getMethod("id").getReturnType()
        } else {
            linkType = null
        }

    }

    fun enumValues() : List<String> {
        if(!enum) return ArrayList(0)

        return EnumDecoder.values(classOf)!!.map { it.name() }
    }

    private val neoDate = SimpleDateFormat("yyyyMMddHHmmssSSS")

    fun fromDataStore(v:Any?) : Any? {
        if(v == null) return v
        if(classOf.isAssignableFrom(javaClass<Date>())) {
            return neoDate.parse(v.toString())
        }
        if(classOf.isEnum()) {
            return EnumDecoder.decode(classOf, v as String)
        }
        if(classOf.equals(javaClass<Int>()) && v is Number) {
            return v.toInt()
        }
        if(classOf.equals(javaClass<Short>()) && v is Number) {
            return v.toShort()
        }
        if(classOf.equals(javaClass<Double>()) && v is Number) {
            return v.toDouble()
        }
        if(classOf.equals(javaClass<Float>()) && v is Number) {
            return v.toFloat()
        }
        return v
    }

    fun toDataStore(v:Any?) : Any? {
        if(v==null) return v
        if(relation && v is LivingElement<*>) {
            return v.id()
        }
        if(classOf.isAssignableFrom(javaClass<Date>())) {
            return java.lang.Long.parseLong(neoDate.format(v))
        }
        if(classOf.isEnum()) {
            return (v as Enum<*>).name()
        }
        return v
    }
}

open class Interest<T : LivingElement<U>, U : Hashable>(val name: String, val target: Class<T>) {
    public val id: Int = Interest.nextId()
    public var offset: Int = 0
        set(v) {
            assert(v >= 0)
            if ($offset != v) {
                $offset = v
                if (limit > 0) {
                    load()
                    emitConfig();
                }
            }
        }
    public var limit: Int = 0
        set(v) {
            assert(v >= 0)
            if (v != $limit) {
                $limit = v
                load()
                emitConfig()
            }
        }
    public val size: Int get() = order.size + offset
    var orderBy: Array<SortKey> = Array(0) { SortKey("", SortDirection.ASC) }

        set(v) {

            log.info("""${id}: setting order: ${v.fold("") {(v, it) -> v + it.property + "." + it.direction.name() + " " }} was ${orderBy.fold("") {(v, it) -> it.property + "." + it.direction.name() + " " }} """)
            if (orderBy != v) {
                $orderBy = v
                log.info("resort")
                resort()
                emitConfig()
            }
        }
    var filter: ElementFilter<T, U> = StaticFilter(this)
        set(v) {
            $filter = v
            if (v !is StaticFilter) load()
        }
    val sourceType: String;
    {
        val jc = javaClass<Entity>()
        var ann = target.getAnnotation(jc)
        sourceType = if (ann != null && !ann!!.name()!!.isEmpty()) ann!!.name()!! else jc.getName()
        load()
    }
    private val galaxy = ch.passenger.kinterest.Universe.galaxy<T, U>(target)!!
    public val descriptor: DomainObjectDescriptor = galaxy.descriptor
    protected val order: MutableList<U> = ArrayList()
    protected val subject: PublishSubject<Event<U>> = PublishSubject.create()!!
    public val observable: Observable<Event<U>> = subject
    protected val comparator: Comparator<U> = object : Comparator<U> {
        override fun compare(id1: U, id2: U): Int {
            val o1 = get(id1)
            val o2 = get(id2)
            if (!(o1 is DomainObject)) throw IllegalArgumentException("compare $o1, $o2")
            if (!(o2 is DomainObject)) throw IllegalArgumentException("compare $o1, $o2")
            if (orderBy.size == 0) return 1
            for (p in orderBy) {
                val dir = if (p.direction == SortDirection.ASC) 1 else -1
                val c1 = o1.get(p.property, descriptor.descriptors[p.property]!!)
                val c2 = o2.get(p.property, descriptor.descriptors[p.property]!!)
                if (c1 == null) if (c2 != null) return -1 * dir
                if (c2 == null) return 1 * dir
                if (!(c1 is Comparable<*>)) throw IllegalArgumentException("compare ${p.property} $c1, $c2")
                if (!(c2 is Comparable<*>)) throw IllegalArgumentException("compare ${p.property} $c1, $c2")

                val v1: Comparable<Any> = c1 as Comparable<Any>
                val v2: Comparable<Any> = c2 as Comparable<Any>

                val res = v1.compareTo(v2) * dir.toInt()
                log.trace("$v1 <=> $v2 -> $res")
                if (res != 0) return res
            }
            return 0
        }
    }
    protected fun load() {
        if (filter.relation == FilterRelations.STATIC) return
        val del = ArrayList(order)
        order.clear()
        del.forEach {
            order.remove(it)
            subject.onNext(RemoveEvent(id, sourceType, it))
        }

        log.info("$name: loading.... $offset->$limit")
        refresh()
    }

    protected fun refresh() {
        val obs = object : Observer<T> {

            override fun onCompleted() {
                log.info("$name: loaded ${order.size}")
                subject.onNext(OrderEvent(id, sourceType, ArrayList(order)))
                if (order.size == 0 && offset > 0) {
                    val off = Math.max(0, offset - limit)
                    if (offset != off) {
                        offset = off
                    }
                }
                emitConfig()
            }
            override fun onError(e: Throwable?) {
                log.error("error on load", e)
            }
            override fun onNext(el: T?) {
                if (el != null) add(el)
            }
        }
        galaxy.filter(filter, orderBy, offset, if (limit > 0) limit + 1 else 0).subscribe(obs)

    }

    public fun add(t: T): Boolean {
        val res = order.add(t.id())
        if (res) subject.onNext(AddEvent(id, sourceType, t.id()))
        if(limit>0) refresh()
        return res
    }

    public fun remove(t: T): Boolean {
        val res = order.remove(t.id())
        subject.onNext(RemoveEvent(id, sourceType, t.id()))
        if(limit>0) refresh()
        return res
    }

    public fun consume(e: ElementEvent<U>) {
        log.info("### consume $e ###")
        when(e) {
            is UpdateEvent<U, *> -> consumeUpdate(e)
            is CreateEvent<U> -> consumeCreate(e)
            is DeleteEvent<U> -> consumeDelete(e)
            else -> {
            }
        }
    }

    private fun consumeDelete(e: DeleteEvent<U>) {
        if (order.remove(e.id)) {
            subject.onNext(e)
            subject.onNext(RemoveEvent(id, sourceType, e.id))
            if(limit>0) refresh()
        }
    }


    private fun consumeCreate(e: CreateEvent<U>) {
        val el = get(e.id)
        log.info("GOT: $el")
        if (el != null && filter.accept(el)) {
            if (!add(el)) return
            subject.onNext(e)
            resort()
        }
    }

    private fun consumeUpdate(e: UpdateEvent<U, *>) {
        val idx = order.indexOf(e.id)
        log.info("interest: $sourceType.$id consume: $e idx: $idx")
        if (idx >= 0) {
            val el = at(idx)
            log.info("UPD: $el")
            if (el == null) return
            if (!filter.accept(el)) {
                log.info("${sourceType}.${e.id} removed coz filter rejects")
                remove(el)
            } else {
                log.info("${sourceType}.${e.id} propagate and sort")
                subject.onNext(e)
                resort()
            }
        } else {
            val ne = galaxy.get(e.id)
            if (ne != null && filter.accept(ne)) {
                log.info("${sourceType}.${e.id} adding coz filter says so")
                add(ne)
                subject.onNext(AddEvent(id, sourceType, ne.id()))
                resort()
            }
        }
    }

    protected open fun resort() {
        if(orderBy.size==0) return
        if(limit>0) {refresh(); return}
        val no = order.sort(comparator)
        val changed = order.withIndices().fold(false) {
            (fl, p) ->
            (p.second != no[p.first]) || fl
        }
        if (changed) {
            order.clear()
            order.addAll(no)
            subject.onNext(OrderEvent(id, sourceType, order))
        }
    }

    fun emitConfig() {
        subject.onNext(InterestConfigEvent<U>(galaxy.kind, id, size, offset, limit, orderBy))
    }

    public fun get(id: U): T? = galaxy.get(id)

    open public fun contains(t: T): Boolean = order.contains(t.id())
    open public fun at(idx: Int): T = get(order[idx])!!
    public fun indexOf(t: U): Int = order.indexOf(t)

    fun close() {
        galaxy.interests.remove(id)
    }

    class object {
        var count: Int = 0
        fun nextId() = count++
    }
}


object Universe {
    private val galaxies: MutableMap<Class<*>, Any> = HashMap()

    public fun descriptor(kind:String) : DomainObjectDescriptor? {
        val g = galaxies.values().filter { it is Galaxy<*,*> && it.kind == kind }.reduce { g,i -> g }
        if(g is Galaxy<*,*>) {
            return g.descriptor
        }
        return null
    }

    public fun starmap(): Iterable<DomainObjectDescriptor> {
        return galaxies.values().map { (it as Galaxy<*, *>).descriptor }
    }

    public fun<T : LivingElement<U>, U : Hashable> galaxy(target: Class<T>): Galaxy<T, U>? {
        return galaxies[target] as Galaxy<T, U>?
    }

    fun<T : LivingElement<U>, U : Hashable> register(g: Galaxy<T, U>) {
        galaxies[g.sourceType] = g
    }

    fun<T : LivingElement<U>, U : Hashable> get(c: Class<T>, id: U): T? {
        val g = galaxies[c] as Galaxy<T, U>?
        if (g != null) return g.get(id)
        throw IllegalStateException("unknown type $c")
    }
}


class Retriever<U : Hashable>(val ids: Iterable<U>, val publisher: EntityPublisher)

abstract class Galaxy<T : LivingElement<U>, U : Hashable>(val sourceType: Class<T>, val store: DataStore<Event<U>, U>) {
    public val descriptor: DomainObjectDescriptor = DomainObjectDescriptor(sourceType)
    val retrievers: BlockingDeque<Retriever<U>> = LinkedBlockingDeque()
    val hasso: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    val heaven: MutableMap<U, T> = WeakHashMap()
    val interests: MutableMap<Int, Interest<T, U>> = WeakHashMap()
    val kind: String;
    val onetoone: MutableMap<String, Relation<LivingElement<Hashable>, Hashable>> = HashMap();
    val onetomany: MutableMap<String, Relation<LivingElement<Hashable>, Hashable>> = HashMap();
    {
        store.schema(sourceType, descriptor)
        var entity: Entity = sourceType.getAnnotation(javaClass<Entity>())!!;
        sourceType.getMethods().forEach {
            val mn = it.getName()!!
            if (mn.startsWith("get")) {
                val pn = mn.substring(3).decapitalize()
                val one = it.getAnnotation(javaClass<OneToOne>())
                if (one != null) {
                    val sn = "set${pn.capitalize()}"
                    var setter: Method? = null
                    sourceType.getMethods().forEach {
                        if (it.getName() == sn) {
                            setter = it
                        }
                    }
                    val targetEntity = one.targetEntity() as Class<LivingElement<Hashable>>
                    if (targetEntity == null) throw IllegalStateException("$pn: OneToOne targetEntity must be defined")
                    onetoone[pn] = Relation(pn, it, setter, targetEntity)
                }
            }
        }

        kind = if (entity.name() == null || entity.name()?.trim()?.length() == 0) sourceType.javaClass.getName() else entity.name()!!
        store.observable.subscribe {
            e ->
            log.info("GALAXY ${this.kind} ### $e ###")
            if (e is DeleteEvent) {
                heaven.remove(e.id)
            }
            if (e is UpdateEvent<U, *>) {
                val el = heaven[e.id]
                if (el != null) {
                    val rel = onetoone[e.property]
                    if (rel != null) {
                        if (rel.setter == null) {
                            throw IllegalStateException("update for immutable ${e.property}")
                        }
                        if (!rel.nullable && e.value == null) throw IllegalStateException("attempt to null property ${e.property} on ${el}")
                        if (el is DomainObject)
                            el.set(e.property, el.descriptor().descriptors[e.property]!!, ch.passenger.kinterest.Universe.get<LivingElement<Hashable>, Hashable>(rel.target, e.value as Hashable))
                    }
                }
            }
            if (e is ElementEvent<U>) interests.values().forEach { it.consume(e) }
        }
        val call = {() ->
            try {
                val drain: MutableList<Retriever<U>> = ArrayList();
                drain.add(retrievers.take())
                retrievers.drainTo(drain);
                log.info("hasso woke up: $drain")
                log.info("$kind retrieving ${drain.size} items")
                drain.forEach { val obo = it.publisher; obo.publish(it.ids.map { this@Galaxy.get(it) }.filterNotNull() as List<LivingElement<Hashable>>) }
            } catch(e: Throwable) {
                log.error("RETRIEVER", e)
            }
        }
        hasso.scheduleAtFixedRate(call, 100, 100, TimeUnit.MILLISECONDS)
    }

    public fun interested(name: String = ""): Interest<T, U> {
        val interest = Interest(name, sourceType as Class<T>)
        interests.put(interest.id, interest)
        return interest
    }

    public fun uninterested(id: Int) {
        interests[id]?.close()
        interests.remove(id)
    }

    class Relation<T : LivingElement<U>, U : Hashable>(val name: String, val getter: Method, val setter: Method?, val target: Class<T>) {
        val nullable: Boolean = getter.getAnnotation(javaClass<Nullable>()) != null
    }

    public fun get(id: U): T? {
        var t: T? = heaven[id]
        if (t == null) {
            val el = retrieve(id); if (el != null) heaven[id] = el; t = el
        }
        return t
    }

    public fun retriever(ids: Iterable<U>, obo: EntityPublisher) {
        retrievers.offer(Retriever(ids, obo))
    }

    abstract protected fun retrieve(id: U): T?
    public fun create(values: Map<String, Any?>) {
        assert(descriptor.uniques.all { values.containsKey(it) })
        store.create(generateId(), values, descriptor)
    }

    abstract public fun generateId(): U


    public fun filter(f: ElementFilter<T, U>, orderBy: Array<SortKey>, offset: Int, limit: Int): Observable<T> {
        return store.filter(f, orderBy, offset, limit).map { if (it == null) throw IllegalStateException(); if (!heaven.containsKey(it)) heaven[it] = get(it)!!; heaven[it]!! }!!
    }

    public fun<X : LivingElement<Y>, Y : Hashable> relation(from: U, to: Class<X>, relation: String): Y? {
        log.info("relation for $from")
        return store.findRelation(kind, from, to.entityName(), relation)
    }
    public fun<X : LivingElement<Y>, Y : Hashable> setRelation(from: T, to: X?, old: X?, relation: String, optional: Boolean) {
        store.setRelation(from, to, old, relation, optional, descriptor)
    }

    public fun<Y : Hashable> createRelation(from: U, toKind: String, to: Y, relation: String, optional: Boolean) {
        store.createRelation(kind, from, toKind, to, relation, optional)
    }

    val filterFactory: FilterFactory<T, U> = FilterFactory(sourceType)
}

fun Class<*>.entityName(): String {
    val ann = getAnnotation(javaClass<Entity>())
    if (ann != null && ann.name() != null && ann.name().isNotEmpty()) return ann.name()!!
    return getName()
}

public fun Method.unique(): Boolean = getAnnotation(javaClass<UniqueConstraint>()) != null
public fun Method.nullable(): Boolean = getAnnotation(javaClass<Nullable>()) != null
public fun Method.index(): Boolean = getAnnotation(javaClass<Index>()) != null
public fun Method.transient(): Boolean = getAnnotation(javaClass<Transient>()) != null
public fun Method.relation(): Boolean = getAnnotation(javaClass<OneToOne>()) != null
public fun Method.relTarget(): Class<*> = getAnnotation(javaClass<OneToOne>())!!.targetEntity()!!
public fun Method.interest(): Boolean = getAnnotation(javaClass<OneToMany>()) != null

public fun Method.propertyName(): String {
    val mn = getName()!!
    if ((mn.startsWith("set") || mn.startsWith("get")) && mn.length > 3) return mn.substring(3).decapitalize()
    if (mn.startsWith("is") && getReturnType()!!.isAssignableFrom(javaClass<Boolean>()) && mn.length > 2) return mn.substring(2).decapitalize()
    return mn
}
