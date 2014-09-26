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
import rx.subjects.PublishSubject
import java.util.Comparator
import javax.persistence.Transient
import javax.persistence.OneToOne
import javax.persistence.Id
import javax.persistence.UniqueConstraint
import ch.passenger.kinterest.annotations.Index
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
import ch.passenger.kinterest.annotations.Label
import javax.persistence.ManyToOne
import ch.passenger.kinterest.util.EntityList
import java.util.AbstractList
import ch.passenger.kinterest.annotations.Expose

/**
 * Created by svd on 11/12/13.
 */
private val log: Logger = LoggerFactory.getLogger("ch.passenger.kinterest.core")!!
//trait Identifiable<T : Hashable>  {
//    public fun id() : T
//}


trait LivingElement<T:Comparable<T>> : Comparable<LivingElement<T>>  {
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
    override fun compareTo(other: LivingElement<T>): Int = id().compareTo(other.id())

    //override fun compareTo(other: LivingElement<T>): Int = id().compa
}



public enum class EventTypes {
    CREATE ADD DELETE REMOVE UPDATE ORDER INTEREST
}

open class Event<U : Comparable<U>>(public val sourceType: String, public val kind: EventTypes)
open class ElementEvent<U : Comparable<U>>(sourceType: String, public val id: U, kind: EventTypes) : Event<U>(sourceType, kind)
open class InterestEvent<U : Comparable<U>>(val interest: Int, sourceType: String, public val id: U, kind: EventTypes) : Event<U>(sourceType, kind)
class UpdateEvent<U : Comparable<U>, V>(sourceType: String, id: U, public val property: String, public val value: V?, public val old: V?)
: ElementEvent<U>(sourceType, id, EventTypes.UPDATE)
class CreateEvent<U : Comparable<U>>(sourceType: String, id: U)
: ElementEvent<U>(sourceType, id, EventTypes.CREATE)
class AddEvent<U : Comparable<U>>(interest: Int, sourceType: String, id: U)
: InterestEvent<U>(interest, sourceType, id, EventTypes.ADD)
class RemoveEvent<U : Comparable<U>>(interest: Int, sourceType: String, id: U)
: InterestEvent<U>(interest, sourceType, id, EventTypes.REMOVE)
class DeleteEvent<U : Comparable<U>>(sourceType: String, id: U)
: ElementEvent<U>(sourceType, id, EventTypes.DELETE)
class OrderEvent<U : Comparable<U>>(val interest: Int, sourceType: String, val order: Iterable<U>) : Event<U>(sourceType, EventTypes.ORDER)
class InterestConfigEvent<U:Comparable<U>>(sourceType:String, val interest:Int, val currentsize:Int, val estimated:Int, val offset:Int, val limit:Int, val orderBy:Array<SortKey>) : Event<U>(sourceType, EventTypes.INTEREST)


trait DataStore<T : Event<U>, U : Comparable<U>> {
    val observable: Observable<T>
    fun<T : LivingElement<U>> filter(f: ElementFilter<T, U>, orderBy: Array<SortKey>, offset: Int, limit: Int): Observable<U>
    /**
     * this is called to initialise non-nullable relations when the root has not yet been created
     */
    fun<V : Comparable<V>> createRelation(fromKind: String, from: U, toKind: String, to: V, relation: String, optional: Boolean)
    fun<V : Comparable<V>> findRelation(fromKind: String, from: U, toKind: String, relation: String): V?
    fun<A : LivingElement<U>, B : LivingElement<V>, V : Comparable<V>> setRelation(from: A, to: B?, old: B?, relation: String, optional: Boolean, desc: DomainObjectDescriptor)
    fun<V : Comparable<V>> setRelation(from: U, to: V?, old: V?, relation: String, optional: Boolean, desc: DomainObjectDescriptor)
    fun<A : LivingElement<U>> deleteRelation(from: A, relation: String, desc: DomainObjectDescriptor)
    fun deleteRelation(from: U, relation: String, desc: DomainObjectDescriptor)
    fun<V : Comparable<V>> findRelations(from: U, relation: String, desc: DomainObjectDescriptor): Observable<V>
    fun <V:Comparable<V>> countRelations(from: U, relation: String, desc: DomainObjectDescriptor): Observable<Int>
    fun <V:Comparable<V>> findNthRelations(from: U, relation: String, nth:Int, desc: DomainObjectDescriptor): Observable<V>
    fun<A : LivingElement<U>, B : LivingElement<V>, V : Comparable<V>> addRelation(from: A, to: B, relation: String, desc: DomainObjectDescriptor)
    fun<V : Comparable<V>> removeRelation(from: U, to: V, relation: String, desc: DomainObjectDescriptor)
    fun getValue(id:U,kind:String, p:String) : Any?
    fun setValue(id:U,kind:String, p:String,v:Any?)
    /**
     * should be called by any galaxy during init to ensure proper schema init (constraints, indices, etc)
     */
    fun schema(cls: Class<*>, desc: DomainObjectDescriptor)
    fun create(id: U, values: Map<String, Any?>, descriptor: DomainObjectDescriptor)

    open public fun<T> atomic(work: ()->T) : T
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
        it.index() && !it.unique()
    }.map { it.propertyName() }
    private val getters: MutableMap<String, Method> = HashMap()
    private val setters: MutableMap<String, Method> = HashMap();
    {
        cls.getMethods().filter {
            !Modifier.isStatic(it.getModifiers()) && !Modifier.isPrivate(it.getModifiers())
            && !it.transient() && (it.getName()!!.startsWith("get") || it.getName()!!.startsWith("is")) && it.getReturnType() != javaClass<Void>()
            && it.getParameterTypes()?.size?:1 == 0 && it.getAnnotation(javaClass<Expose>()) == null
        }.forEach {
            getters.put(it.propertyName(), it)
        }
        cls.getMethods().filter {
            !Modifier.isStatic(it.getModifiers()) && !Modifier.isPrivate(it.getModifiers())
            && !it.transient() && it.getName()!!.startsWith("set") && it.getReturnType() != javaClass<Void>()
        }.forEach {
            setters.put(it.propertyName(), it)
        }
    }

    public val identifier :Method = cls.getMethods().filter { it.getAnnotation(javaClass<Id>()) != null }.first!!

    public val properties: Iterable<String> = getters.keySet()

    public fun nullable(p:String) : Boolean = ch.passenger.kinterest.Universe.galaxy<LivingElement<Comparable<Any>>,Comparable<Any>>(cls.entityName())!!.nullables.containsItem(p)

    public val descriptors: Map<String, DomainPropertyDescriptor>

    {
        val m: MutableMap<String, DomainPropertyDescriptor> = HashMap()
        getters.entrySet().forEach {
            val setter = setters[it.key]
            m[it.key] = DomainPropertyDescriptor(it.key, it.value, setter, {nullable(it.key)})
        }
        descriptors = m
    }
}

class DomainPropertyDescriptor(val property: String, val getter: Method, val setter: Method?, val chknull:()->Boolean) {

    val relation: Boolean = getter.relation()
    val enum : Boolean = getter.getReturnType()!!.isEnum()
    val nullable : Boolean get() = chknull()
    val oneToMany : Boolean get() = getter.oneToMany()
    val classOf: Class<*> = if(oneToMany) getter.getAnnotation(javaClass<OneToMany>())!!.targetEntity()!! else getter.getReturnType()!!
    val targetEntity : String = classOf.entityName()
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

    public override fun toString() : String {
        return "${property}: rel = ${relation} classOf: ${classOf}"
    }
}

open class Interest<T : LivingElement<U>, U : Comparable<U>>(val name: String, val target: Class<T>) {
    public val id: Int = Interest.nextId()
    private val EMPTYORDER : List<U> = ArrayList()
    protected val order: MutableList<U> = ArrayList()
    protected val subject: PublishSubject<Event<U>> = PublishSubject.create()!!
    public val observable: Observable<Event<U>> = subject
    private val traps : MutableSet<Interest<LivingElement<Comparable<Any>>,Comparable<Any>>> = HashSet()

    var offset: Int = 0
        private set(v) {$offset=v}
    var limit: Int = 0
        private set(v) {$limit=v}


    fun buffer(off:Int, lim:Int) {
        assert(off>=0 && lim>=0)
        offset = off
        limit = lim
        load()
        emitConfig()
    }
    public val estimatedsize: Int get() {
        return if(filter.relation==FilterRelations.STATIC) order.size
        else {
            if(order.size()<limit)
                order.size + offset
            else order.size + offset + 1
        }
    }

    public val currentsize : Int get() {
        return if(filter.relation==FilterRelations.STATIC) order.size
        else order.size + offset
    }

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
            if (v !is StaticFilter) {
                trapper(v)
                load()
            }
        }
    private fun trapper(f:ElementFilter<T,U>) {
        val ts = HashSet(traps)
        ts.forEach { it.close() }
        traps.clear()

        if (f is CombinationFilter<T, U>) {
            f.combination.forEach { trapper(it) }
        } else if (f is RelationFilter<T, U>) {
            if (f.relation == FilterRelations.TO) {
                val rf = f.f
                val rel = f.property
                val pd = descriptor.descriptors[rel]!!
                val g = ch.passenger.kinterest.Universe.galaxy<LivingElement<Comparable<Any>>, Comparable<Any>>(pd.targetEntity)!!
                val i = g.interested("$id.$rel")
                i.subject.filter {
                    log.info("TRAP: $it");
                    it is OrderEvent<*> || it is AddEvent<*> || it is RemoveEvent<*> || (it is UpdateEvent<*, *> && it.property == rel)
                }!!.subscribe {
                    log.info("trap refresh $it")
                    refresh()
                }
                i.buffer(0, 1)
                i.filter = rf as ElementFilter<LivingElement<Comparable<Any>>, Comparable<Any>>
                traps.add(i as Interest<LivingElement<Comparable<Any>>, Comparable<Any>>)
            } else {
                val rf = f.f
                val rel = f.property
                val g = ch.passenger.kinterest.Universe.galaxy<LivingElement<Comparable<Any>>, Comparable<Any>>(rf.target.entityName())!!
                val pd = g.descriptor.descriptors[rel]!!
                val i = g.interested("$id.$rel")
                i.subject.filter {
                    log.info("TRAP: $it");
                    it is OrderEvent<*> || it is AddEvent<*> || it is RemoveEvent<*> || (it is UpdateEvent<*, *> && it.property == rel)
                }!!.subscribe {
                    log.info("trap refresh $it")
                    refresh()
                }
                i.buffer(0, 1)
                i.filter = rf as ElementFilter<LivingElement<Comparable<Any>>, Comparable<Any>>
                traps.add(i as Interest<LivingElement<Comparable<Any>>, Comparable<Any>>)
            }
        }
    }
    val sourceType: String;
    {
        val jc = javaClass<Entity>()
        var ann = target.getAnnotation(jc)
        sourceType = if (ann != null && !ann!!.name()!!.isEmpty()) ann!!.name()!! else jc.getName()
        load()
    }
    private val galaxy = ch.passenger.kinterest.Universe.galaxy<T,U>(target.entityName())!! as Galaxy<T,U>
    public val descriptor: DomainObjectDescriptor = galaxy.descriptor

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
        if (filter.relation == FilterRelations.STATIC) {emitOrder(); return}
        order.clear()
        log.info("$name: loading.... $offset->$limit")
        refresh()
    }

    fun emitOrder() {
        if(order==null) {
            val e = Exception("not thrown. non-nullable val order was null $order==null ${order==null}")
            log.warn("bad order", e)
            return
        }
        subject.onNext(OrderEvent(id, sourceType, currentSlice()))
    }

    fun currentSlice() : List<U> {
        if(order==null ||order.size==0) return EMPTYORDER
        val order = ArrayList(this.order)
        if(filter.relation == FilterRelations.STATIC) {
            return if (limit > 0) order.subList(if (offset >= order.size) 0 else offset, Math.min(order.size, offset + limit)) else order
        } else {
            return if(limit>0) order.subList(0, Math.min(order.size, limit)) else order
        }
    }

    public fun refresh() {
        if(filter.relation==FilterRelations.STATIC) {
            emitOrder()
            return
        }
        order.clear()
        val obs = object : Observer<T> {

            override fun onCompleted() {
                log.info("$name: loaded ${order.size}")
                emitOrder()
                if (order.size == 0 && offset > 0) {
                    val off = Math.max(0, offset - limit)
                    log.info("re-adjust offset $offset -> $off")
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
                if (el != null && !order.containsItem(el.id())) order.add(el.id())
            }
        }
        galaxy.filter(filter, orderBy, offset, if (limit > 0) limit + 1 else 0).subscribe(obs)

    }

    public fun add(aid: U): Boolean {
        if(order.contains(aid)) return false
        val res = order.add(aid)
        if (res) subject.onNext(AddEvent(id, sourceType, aid))
        if(limit>0) refresh()
        return res
    }

    public fun remove(aid: U): Boolean {
        val res = order.remove(aid)
        subject.onNext(RemoveEvent(id, sourceType, aid))
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
            if (!add(el.id())) return
            subject.onNext(e)
            resort()
        }
    }

    private fun consumeUpdate(e: UpdateEvent<U, *>) {
        val idx = order.indexOf(e.id)
        log.info("interest: $sourceType.$id consume: $e idx: $idx")
        if (idx >= 0) {
            val el = galaxy.get(e.id)
            log.info("UPD: $el")
            if (el == null) {log.warn("Galaxy does not know ${e.id}"); return}
            if (!filter.accept(el)) {
                log.info("${sourceType}.${e.id} removed coz filter rejects")
                remove(el.id())
            } else {
                log.info("${sourceType}.${e.id} propagate and sort")
                subject.onNext(e)
                resort()
            }
        } else {
            val ne = galaxy.get(e.id)
            if (ne != null && filter.accept(ne)) {
                log.info("${sourceType}.$name.$id.${e.id} adding coz filter says so")
                add(ne.id())
                subject.onNext(AddEvent(id, sourceType, ne.id()))
                resort()
            } else {
                log.info("${name}.$id ${e.id} no action")
            }
        }
    }

    protected open fun resort() {
        if(limit>0&&filter.relation!=FilterRelations.STATIC) {refresh(); return}
        if(orderBy.size==0) return
        val no = order.sort(comparator)
        val changed = order.withIndices().fold(false) {
            (fl, p) ->
            (p.second != no[p.first]) || fl
        }
        if (changed) {
            log.info("order changed")
            order.clear()
            order.addAll(no)
            emitOrder()
        } else {
            log.info("no change no order")
        }
    }

    fun emitConfig() {
        subject.onNext(InterestConfigEvent<U>(galaxy.kind, id, currentsize, estimatedsize, offset, limit, orderBy))
    }

    public fun get(id: U): T? = galaxy.get(id)

    open public fun contains(t: T): Boolean = order.contains(t.id())
    open public fun at(idx: Int): T {
        if(idx-offset<order.size())
        return get(order[idx-offset])!!
        else {
            //provocate index out of bounds
            if(idx>=currentsize) return get(order[idx])!!
            return galaxy.filter(filter, orderBy, idx, 1).timeout(500, TimeUnit.MILLISECONDS)!!.toBlockingObservable()!!.single()!!
        }
    }
    public fun indexOf(t: U): Int = order.indexOf(t)

    fun close() {
        subject.onCompleted()
        traps.forEach { it.close() }
    }

    fun orderDo(cb:(U)->Unit) {
        order.forEach { if(it!=null) cb(it) }
    }

    class object {
        var count: Int = 0
        fun nextId() = count++
    }
}


object Universe {
    private val galaxies: MutableMap<String, Any> = HashMap()

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

    public fun galaxy<T:LivingElement<U>,U:Comparable<U>>(entity:String): Galaxy<T,U>? {
        return galaxies[entity] as Galaxy<T,U>?
    }

    fun<T : LivingElement<U>, U : Comparable<U>> register(g: Galaxy<T, U>) {
        galaxies[g.descriptor.entity] = g
    }

    fun<T : LivingElement<U>, U : Comparable<U>> get(c: Class<T>, id: U): T? {
        val g = galaxies[c.entityName()] as Galaxy<T, U>?
        if (g != null) return g.get(id)
        throw IllegalStateException("unknown type $c")
    }

    fun get(kind:String, id:Comparable<*>) : LivingElement<Comparable<Any>>? {
        val g = galaxies[kind] as Galaxy<LivingElement<Comparable<Any?>>, Comparable<Any?>>
        return g.get(id as Comparable<Any?>) as LivingElement<Comparable<Any>>
    }
}


class Retriever<U : Comparable<U>>(val ids: Iterable<U>, val publisher: EntityPublisher)

abstract class Galaxy<T : LivingElement<U>, U : Comparable<U> >(val sourceType: Class<T>, val store: DataStore<Event<U>, U>) {
    public val descriptor: DomainObjectDescriptor = DomainObjectDescriptor(sourceType)
    val retrievers: BlockingDeque<Retriever<U>> = LinkedBlockingDeque()
    val hasso: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    val heaven: MutableMap<U, T> = WeakHashMap()
    val interestLock = ReentrantReadWriteLock()
    private val interests: MutableMap<Int, Interest<T, U>> = HashMap()
    val kind: String;
    val onetomany: MutableMap<String, Relation> = HashMap();



    public fun withInterestDo(cb:(Set<Interest<T,U>>)->Unit) {
        var kl : MutableSet<Interest<T,U>> = HashSet()
        interestLock.readLock().lock()
        try {
            kl.addAll(interests.values())
        } finally {
            interestLock.readLock().unlock()
        }
        cb(kl)
    }

    public fun withInterestDo(id:Int, cb:(Interest<T,U>)->Unit) {
        var i : Interest<T,U>? = null
        interestLock.readLock().lock()
        try {
            i = interests[id]
        } finally {
            interestLock.readLock().unlock()
        }
        if(i!=null) cb(i!!)
    }

    {
        store.schema(sourceType, descriptor)
        var entity: Entity = sourceType.getAnnotation(javaClass<Entity>())!!;
        sourceType.getMethods().filter { it.getName()!!.startsWith("get")  }.forEach {
            val mn = it.getName()!!
            val pn = mn.substring(3).decapitalize()
            val many = it.getAnnotation(javaClass<ManyToOne>())
            if(many!=null) {
                onetomany[pn] = Relation(pn, descriptor.descriptors[pn]!!)
            }

        }

        kind = if (entity.name() == null || entity.name()?.trim()?.length() == 0) sourceType.javaClass.getName() else entity.name()!!
        val obs = object : rx.Observer<Event<U>> {

            override fun onCompleted() {
                log.warn("complteted on Galaxy.${descriptor.entity}")
            }
            override fun onError(e: Throwable?) {
                log.error("error", e)
                e?.printStackTrace()
            }
            override fun onNext(e: Event<U>?) {
                log.info("GALAXY ${kind} ### $e ###")
                if (e is DeleteEvent) {
                    heaven.remove(e.id)
                }
                if(e is UpdateEvent<U,*>) {
                    val p = e.property
                    val pd = descriptor.descriptors[p]!!
                    withInterestDo { it.forEach { it.consume(e) } }
                }
                if (e is ElementEvent<U>) withInterestDo { it.forEach { it.consume(e) } }
            }
        }
        store.observable.filter { log.info("!!!###$it ${it?.sourceType}"); it!=null && it.sourceType == kind }?.subscribe(obs)
        val call = {() ->
            try {
                val drain: MutableList<Retriever<U>> = ArrayList();
                drain.add(retrievers.take())
                retrievers.drainTo(drain);
                log.info("hasso woke up: $drain")
                log.info("$kind retrieving ${drain.size} items")
                drain.forEach { val obo = it.publisher; obo.publish(it.ids.map { this@Galaxy.get(it) }.filterNotNull() as List<LivingElement<Comparable<Any>>>) }
            } catch(e: Throwable) {
                log.error("RETRIEVER", e)
            }
        }
        hasso.scheduleAtFixedRate(call, 100, 100, TimeUnit.MILLISECONDS)
    }

    public fun interested(name: String = ""): Interest<T, U> {
        val interest = Interest(name, sourceType as Class<T>)
        interestLock.writeLock().lock()
        try {
            interests.put(interest.id, interest)
        } finally {
            interestLock.writeLock().unlock()
        }
        return interest
    }

    public fun uninterested(id: Int) {
        interests[id]?.close()
        interestLock.writeLock().lock()
        try {
            interests.remove(id)
        } finally {
            interestLock.writeLock().unlock()
        }
    }

    class Relation(val name: String, val pd:DomainPropertyDescriptor)

    public fun get(id: U): T? {
        var t: T? = heaven[id]
        if (t == null) {
            val el = retrieve(id);
            if (el != null) heaven[id] = el;
            t = el
        }
        return t
    }

    public fun retriever(ids: Iterable<U>, obo: EntityPublisher) {
        retrievers.offer(Retriever(ids, obo))
    }

    abstract protected fun retrieve(id: U): T?
    public fun create(values: Map<String, Any?>) : U {
        assert(descriptor.uniques.all { values.containsKey(it) })
        val gid = generateId()
        store.create(gid, values, descriptor)
        return gid
    }

    abstract public fun generateId(): U


    public fun filter(f: ElementFilter<T, U>, orderBy: Array<SortKey>, offset: Int, limit: Int): Observable<T> {
        return store.filter(f, orderBy, offset, limit).map { if (it == null) throw IllegalStateException(); if (!heaven.containsKey(it)) heaven[it] = get(it)!!; heaven[it]!! }!!
    }

    public fun<X : LivingElement<Y>, Y : Comparable<Y>> relation(from: U, to: Class<X>, relation: String): Y? {
        log.info("relation for $from")
        return store.findRelation<Y>(kind, from, to.entityName(), relation)
    }
    public fun<X : LivingElement<Y>, Y : Comparable<Y>> setRelation(from: T, to: X?, old: X?, relation: String, optional: Boolean) {
        store.setRelation(from, to, old, relation, optional, descriptor)
    }

    public fun<Y : Comparable<Y>> createRelation(from: U, toKind: String, to: Y, relation: String, optional: Boolean) {
        store.createRelation(kind, from, toKind, to, relation, optional)
    }

    public fun getValue(target:U, p:String) : Any? {
        val pd = descriptor.descriptors[p]!!
        if(pd.relation) {
            val id = store.findRelation<Comparable<Any?>>(descriptor.entity, target, pd.targetEntity, p)
            if(id==null) return null
            return ch.passenger.kinterest.Universe.galaxy<LivingElement<Comparable<Any?>>,Comparable<Any?>>(pd.targetEntity)?.get(id)
        } else if(pd.oneToMany) return pd.getter.invoke(get(target))
        else return pd.fromDataStore(store.getValue(target, descriptor.entity, p))
    }

    public fun setValue(target:U, p:String, v:Any?) {
        val pd = descriptor.descriptors[p]!!
        if(pd.relation) {
            store.setRelation<Comparable<Any>>(target, v as Comparable<Any>?, getValue(target, p) as Comparable<Any>?, p, pd.nullable, descriptor)
        } else if(!pd.oneToMany) {
            store.setValue(target, descriptor.entity, p, pd.toDataStore(v))
        }
    }

    val filterFactory: FilterFactory<T, U> = FilterFactory(this, sourceType, descriptor)
    abstract val nullables : Set<String>
    public fun isNullable(p:String) : Boolean = nullables.containsItem(p)
}

fun Class<*>.entityName(): String {
    val ann = getAnnotation(javaClass<Entity>())
    if (ann != null && ann.name() != null && ann.name().isNotEmpty()) return ann.name()!!
    return getName()
}

class EntityAction(val method:Method) {
    val name : String = method.getName()!!
    val returns : Class<*> = method.getReturnType()!!
}

fun Class<*>.exposeds() : List<EntityAction> {
    val res = ArrayList<EntityAction>()
    getMethods().forEach {
        if(it.getAnnotation(javaClass<Expose>())!=null) {
            res.add(EntityAction(it))
        }
    }
    return res
}

public fun Method.unique(): Boolean = getAnnotation(javaClass<UniqueConstraint>()) != null
public fun Method.index(): Boolean = getAnnotation(javaClass<Index>()) != null
public fun Method.transient(): Boolean = getAnnotation(javaClass<Transient>()) != null
public fun Method.relation(): Boolean = getAnnotation(javaClass<OneToOne>()) != null
public fun Method.relTarget(): Class<*> = getAnnotation(javaClass<OneToOne>())!!.targetEntity()!!
public fun Method.oneToMany(): Boolean = getAnnotation(javaClass<OneToMany>()) != null
public fun Method.label(): Boolean = getAnnotation(javaClass<Label>()) != null

public fun Method.propertyName(): String {
    val mn = getName()!!
    if ((mn.startsWith("set") || mn.startsWith("get")) && mn.length > 3) return mn.substring(3).decapitalize()
    if (mn.startsWith("is") && getReturnType()!!.isAssignableFrom(javaClass<Boolean>()) && mn.length > 2) return mn.substring(2).decapitalize()
    return mn
}
