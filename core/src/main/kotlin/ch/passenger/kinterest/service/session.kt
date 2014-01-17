package ch.passenger.kinterest.service

import java.security.Principal
import ch.passenger.kinterest.DomainObjectDescriptor
import java.util.HashMap
import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.Galaxy
import ch.passenger.kinterest.ElementFilter
import ch.passenger.kinterest.Interest
import rx.Observable
import com.fasterxml.jackson.databind.node.ObjectNode
import ch.passenger.kinterest.util.json.Jsonifier
import org.slf4j.LoggerFactory
import ch.passenger.kinterest.FilterRelations
import ch.passenger.kinterest.StaticFilter
import ch.passenger.kinterest.Event
import java.util.HashSet
import rx.Subscription
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import com.fasterxml.jackson.databind.node.ArrayNode
import ch.passenger.kinterest.SortKey
import ch.passenger.kinterest.SortDirection
import ch.passenger.kinterest.util.EntityList
import ch.passenger.kinterest.Universe

/**
 * Created by svd on 18/12/13.
 *
 */

public open class KIPrincipal(val principal: String, val token: String) : Principal {


    override fun getName(): String? = principal

    override fun equals(obj: Any?): Boolean {
        if (obj is KIPrincipal) return token == obj.token
        return false
    }
    override fun hashCode(): Int = token.hashCode()

    class object {
        val ANONYMOUS = KIPrincipal("guest", "")
    }
}

public open class KISession(val principal: KIPrincipal, val app:KIApplication) {
    fun current() = current.set(this)
    fun detach() = current.set(null)
    val id = KISession.nid()
    val interests : MutableMap<Int,Interest<LivingElement<Comparable<Any?>>,Comparable<Any?>>> = HashMap()
    val subjects : MutableMap<Int,Subscription> = HashMap()
    var events : EventPublisher? = null
    set(v) {
        if($events==null && v!=null) {
            interests.values().forEach {
                val subscription = it.observable.subscribe{if(it is Event<*>) events?.publish(listOf(it as Event<Comparable<Any>>))}!!
                subjects[it.id] = subscription
            }
        } else if($events!=null&&v==null) {
            subjects.values().forEach { it.unsubscribe() }
            subjects.clear()
        }
        $events = v
    }
    var entities : EntityPublisher? = null

    fun addInterest(i:Interest<LivingElement<Comparable<Any?>>,Comparable<Any?>>) {
        interests[id] = i
        if(events!=null) {
            subjects[i.id] = i.observable.subscribe{events?.publish(listOf(it!! as Event<Comparable<Any>>))}!!
        }
    }
    fun removeInterest(i:Interest<*,*>) {
        interests.remove(i)
        subjects[i.id]?.unsubscribe()
    }

    fun dispose() {
        events = null
        entities = null
        interests.values().forEach { it.close() }
    }

    fun allInterests(cb:(Interest<out LivingElement<Comparable<Any>>,out Comparable<Any>>)->Unit) {
        log.info("iterating ${interests.size()} interests")
        interests.values().forEach { cb(it as Interest<out LivingElement<Comparable<Any>>,out Comparable<Any>>) }
    }


    class object {
        private val log = LoggerFactory.getLogger("KISession")!!
        private var nid = 0
        fun nid() = ++nid
        val current: ThreadLocal<KISession> = ThreadLocal()
        public fun current(): KISession? {
            log.info("${current.get()}")
            return current.get()
        }
    }
}

class KIApplication(val name: String, val descriptors: Iterable<ServiceDescriptor<*>>) {
    val retriever : ExecutorService = Executors.newCachedThreadPool()
}

public abstract class ServiceDescriptor<T : KIService>(val service: Class<T>) {
    public abstract fun create(): T
}

public class SimpleServiceDescriptor<T : KIService>(service: Class<T>, val creator: () -> T) : ServiceDescriptor<T>(service) {

    override fun create(): T = creator()
}


public open class KIService(name: String) {
    public val id: Int = ni()
    class object {
        private var ni: Int = 0
        private fun ni() = ++ni
    }
}

public open class InterestService<T : LivingElement<U>, U : Comparable<U>>(val galaxy: Galaxy<T, U>) : KIService(galaxy.descriptor.entity) {
    private val log = LoggerFactory.getLogger(this.javaClass)!!
    public fun create(name: String = ""): Int  {
        val interest = galaxy.interested(name)
        KISession.current()!!.addInterest(interest as Interest<LivingElement<Comparable<Any?>>,Comparable<Any?>>)
        log.info("${KISession.current()!!.id} created interest ${interest.id} now: ${KISession.current()?.interests?.size}")
        return interest.id
    }
    public fun delete(id: Int): Unit = galaxy.uninterested(id)
    public fun filter(id: Int, filter: ObjectNode): Unit {
        log.info("$filter")
        galaxy.withInterestDo {
            val interest = it[id]

            if (interest != null) {
                if (filter["relation"]!!.textValue()!! == FilterRelations.STATIC.name()) {
                    interest.filter = StaticFilter(interest)
                } else {
                    val f = galaxy.filterFactory.fromJson(filter)
                    interest.filter = f
                }
            }
        }

    }
    public fun orderBy(id:Int, order:ArrayNode) {
        galaxy.withInterestDo {
            val i = it[id]
            if(i is Interest) {
                log.info("$order")
                i.orderBy = order.map { SortKey(it.get("property")!!.textValue()!!, SortDirection.valueOf(it.get("direction")!!.textValue()!!)) }.copyToArray()
            } else {
                log.warn("cant order dead interest $id")
            }
        }

    }
    public fun buffer(id: Int, offset:Int, limit: Int) {
        galaxy.withInterestDo {
            val interest = it[id]
            if (interest != null) {
                interest.buffer(offset, limit)
            }
        }
    }

    public fun createElement(values: Map<String, Any?>): Unit {
        galaxy.create(values)
    }

    public fun save(json: ObjectNode) {
        log.info("save: ${json}")
        bulkUpdate(Jsonifier.idOf(json) as U, Jsonifier.valueMap(json, galaxy.descriptor))
    }
    public fun bulkUpdate(id: U, values: Map<String, Any?>) {
        values.entrySet().forEach {
            galaxy.setValue(id, it.key, it.value)
        }
    }

    public fun retrieve(ids:jet.Iterable<U>) {
        val session = KISession.current()
        session!!.app.retriever.execute {
            ids.forEach {
                val value = galaxy.get(it)
                if(value!=null) session.entities?.publish(listOf(value as LivingElement<Comparable<Any>>))
            }
        }
    }

    public fun add(id:Int, eid:Comparable<Any>) {
        KISession.current()!!.interests.values().filter { it.id == id }.forEach { val ai = it as Interest<T,U>; it.add(eid as U) }
    }

    public fun remove(id:Int, eid:Comparable<Any>) {
        KISession.current()!!.interests.values().filter { it.id == id }.forEach { val ai = it as Interest<T,U>; it.remove(eid as U) }
    }

    public fun add(eid:Comparable<*>, property:String, target:Comparable<*>) {
        val entity = galaxy.get(eid as U) as LivingElement<U>?
        if(entity is ch.passenger.kinterest.LivingElement<U>) {
            val el = galaxy.getValue(entity.id(), property) as EntityList<T,U,LivingElement<Comparable<Any>>,Comparable<Any>>
            val te = Universe.galaxy<LivingElement<Comparable<Any>>,Comparable<Any>>(galaxy.descriptor.descriptors[property]!!.targetEntity)!!
            el.add(te.get(target as Comparable<Any>) as LivingElement<Comparable<Any>>)
        }
    }

    public fun remove(eid:Comparable<*>, property:String, target:Comparable<*>) {
        val entity = galaxy.get(eid as U) as LivingElement<U>?
        if(entity is ch.passenger.kinterest.LivingElement<U>) {
            val el = galaxy.getValue(entity.id(), property) as EntityList<T,U,LivingElement<Comparable<Any>>,Comparable<Any>>
            val te = Universe.galaxy<LivingElement<Comparable<Any>>,Comparable<Any>>(galaxy.descriptor.descriptors[property]!!.targetEntity)!!
            el.remove(te.get(target as Comparable<Any>) as LivingElement<Comparable<Any>>)
        }
    }

    public fun relint(eid:Comparable<*>, property:String, name:String) : Int {
        val entity = galaxy.get(eid as U) as LivingElement<U>?
        if(entity is ch.passenger.kinterest.LivingElement<U>) {
            val el = galaxy.getValue(entity.id(), property) as EntityList<T,U,LivingElement<Comparable<Any>>,Comparable<Any>>

            val interest = el.asInterest(name)
            KISession.current()?.addInterest(interest as Interest<LivingElement<Comparable<Any?>>,Comparable<Any?>>)
            return interest.id
        }
        throw IllegalStateException()
    }

    public fun refresh(id:Int) {
        val interest = KISession.current()?.interests?.get(id)
        if(interest != null) {
            interest.refresh()
        }
    }


    public fun clear(id:Int) {
        KISession.current()!!.interests.values().filter { it.id == id }.forEach { val ai = it as Interest<T,U>; ai.filter= galaxy.filterFactory.staticFilter(ai) }
    }
}


public trait EventPublisher {
    fun publish(events: jet.Iterable<Event<Comparable<Any>>>)
}

public trait EntityPublisher {
    fun publish(entities: jet.Iterable<LivingElement<Comparable<Any>>>)
}