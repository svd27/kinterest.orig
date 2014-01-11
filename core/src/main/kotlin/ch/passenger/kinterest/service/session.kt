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
    val interests : MutableSet<Interest<*,*>> = HashSet()
    val subjects : MutableMap<Int,Subscription> = HashMap()
    var events : EventPublisher? = null
    set(v) {
        if($events==null && v!=null) {
            interests.forEach {
                val subscription = it.observable.subscribe{events?.publish(listOf(it!!))}!!
                subjects[it.id] = subscription
            }
        } else if($events!=null&&v==null) {
            subjects.values().forEach { it.unsubscribe() }
            subjects.clear()
        }
        $events = v
    }
    var entities : EntityPublisher? = null

    fun addInterest(i:Interest<*,*>) {
        interests.add(i)
        if(events!=null) {
            subjects[i.id] = i.observable.subscribe{events?.publish(listOf(it!!))}!!
        }
    }
    fun removeInterest(i:Interest<*,*>) {
        interests.remove(i)
        subjects[i.id]?.unsubscribe()
    }

    fun dispose() {
        events = null
        entities = null
        interests.forEach { it.close() }
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

public open class InterestService<T : LivingElement<U>, U : Hashable>(val galaxy: Galaxy<T, U>) : KIService(galaxy.descriptor.entity) {
    private val log = LoggerFactory.getLogger(this.javaClass)!!
    public fun create(name: String = ""): Int  {
        val interest = galaxy.interested(name)
        KISession.current()!!.addInterest(interest as Interest<*,*>)
        return interest.id
    }
    public fun delete(id: Int): Unit = galaxy.uninterested(id)
    public fun filter(id: Int, filter: ObjectNode): Unit {
        log.info("$filter")
        val interest = galaxy.interests[id]

        if (interest != null) {
            if (filter["relation"]!!.textValue()!! == FilterRelations.STATIC.name()) {
                interest.filter = StaticFilter(interest)
                return
            }
            val f = galaxy.filterFactory.fromJson(filter)
            interest.filter = f
        }
    }
    public fun orderBy(id:Int, order:ArrayNode) {
        val i = galaxy.interests[id]!!
        log.info("$order")
        i.orderBy = order.map { SortKey(it.get("property")!!.textValue()!!, SortDirection.valueOf(it.get("direction")!!.textValue()!!)) }.copyToArray()
    }
    public fun buffer(id: Int, limit: Int) {
        val interest = galaxy.interests[id]
        if (interest != null) {
            interest.limit = limit
        }
    }

    public fun offset(id: Int, offset: Int) {
        val interest = galaxy.interests[id]
        if (interest != null) {
            interest.offset = offset
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
        val el = galaxy.get(id)!!
        val dd = galaxy.descriptor
        values.entrySet().forEach {
            dd.set(el, it.key, it.value)
        }
    }

    public fun retrieve(ids:jet.Iterable<U>) {
        val session = KISession.current()
        session!!.app.retriever.execute {
            ids.forEach {
                val value = galaxy.get(it)
                if(value!=null) session.entities?.publish(listOf(value))
            }
        }
    }

    public fun add(id:Int, eid:Hashable) {
        KISession.current()!!.interests.filter { it.id == id }.forEach { val ai = it as Interest<T,U>; it.add(eid as U) }
    }

    public fun remove(id:Int, eid:Hashable) {
        KISession.current()!!.interests.filter { it.id == id }.forEach { val ai = it as Interest<T,U>; it.remove(eid as U) }
    }

    public fun clear(id:Int) {
        KISession.current()!!.interests.filter { it.id == id }.forEach { val ai = it as Interest<T,U>; ai.filter= galaxy.filterFactory.staticFilter(ai) }
    }
}


public trait EventPublisher {
    fun publish(events: jet.Iterable<Event<*>>)
}

public trait EntityPublisher {
    fun publish(entities: jet.Iterable<LivingElement<*>>)
}