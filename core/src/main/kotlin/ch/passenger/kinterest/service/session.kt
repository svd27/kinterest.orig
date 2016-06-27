package ch.passenger.kinterest.service

import ch.passenger.kinterest.*
import ch.passenger.kinterest.util.EntityList
import ch.passenger.kinterest.util.json.Jsonifier
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import rx.Observer
import rx.Subscription
import java.security.Principal
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Created by svd on 18/12/13.
 *
 */

public open class KIPrincipal(val principal: String, val token: String) : Principal {


    override fun getName(): String = principal

    override fun equals(obj: Any?): Boolean {
        if (obj is KIPrincipal) return token == obj.token
        return false
    }
    override fun hashCode(): Int = token.hashCode()

    companion  object {
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
        if(field ==null && v!=null) {
            interests.values.forEach {
                val subscription = it.observable.subscribe{if(it is Event<*>) events?.publish(listOf(it as Event<Comparable<Any>>))}!!
                subjects[it.id] = subscription
            }
        } else if(field !=null&&v==null) {
            subjects.values.forEach { it.unsubscribe() }
            subjects.clear()
        }
        field = v
    }
    var entities : EntityPublisher? = null

    fun addInterest(i: Interest<LivingElement<Comparable<Any?>>, Comparable<Any?>>) {
        interests[i.id] = i
        subjects[i.id] = i.observable.subscribe(object :Observer<Event<Comparable<Any?>>> {
            override fun onCompleted() {
                subjects[i.id]?.unsubscribe()
                interests[i.id]?.close()
                interests.remove(i.id)
                log.warn("interest $id done")
            }
            override fun onError(e: Throwable?) {
                log.error(e?.message, e)
                e?.printStackTrace()
            }
            override fun onNext(args: Event<Comparable<Any?>>?) {
                events?.publish(listOf(args!! as Event<Comparable<Any>>))
            }
        })!!
    }
    fun removeInterest(i:Interest<*,*>) {
        interests.remove(i.id)
        subjects[i.id]?.unsubscribe()
    }

    fun dispose() {
        events = null
        entities = null
        interests.values.forEach { it.close() }
    }

    fun allInterests(cb:(Interest<out LivingElement<Comparable<Any>>,out Comparable<Any>>)->Unit) {
        log.info("iterating ${interests.size} interests")
        interests.values.forEach { cb(it as Interest<out LivingElement<Comparable<Any>>,out Comparable<Any>>) }
    }


    companion  object {
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

object TicketBooth {
    private var current : Int = 0
    public fun book() : Int = current++
}

enum class RequestState {
    STARTED, SUCCEEDED, FAILED
}

open class KIServiceRequest<T>() {
    public val ticket : Int = TicketBooth.book()
    public var state : RequestState = RequestState.STARTED
      protected set(v) {
          field =v}
    protected var result : T? = null
    protected var error : Exception? = null
    public fun result() : T {
        if(state==RequestState.SUCCEEDED) {
            return result!!
        }
        if(state==RequestState.FAILED)
            throw error!!

        throw IllegalStateException()
    }
}

public open class KIService(name: String) {
    public val id: Int = ni()
    companion  object {
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
        galaxy.withInterestDo(id) {
            val interest = it
            if (filter["relation"]!!.textValue()!! == FilterRelations.STATIC.name) {
                interest.filter = StaticFilter(interest)
            } else {
                val f = galaxy.filterFactory.fromJson(filter)
                interest.filter = f
            }
        }

    }
    public fun orderBy(id:Int, order:ArrayNode) {
        galaxy.withInterestDo(id) {
            log.info("$order")
            it.orderBy = order.map { SortKey(it.get("property")!!.textValue()!!, SortDirection.valueOf(it.get("direction")!!.textValue()!!)) }.toTypedArray()
        }

    }
    public fun buffer(id: Int, offset:Int, limit: Int) {
        galaxy.withInterestDo(id) {
            it.buffer(offset, limit)
        }
    }

    public fun createElement(values: Map<String, Any?>): U {
        return galaxy.create(values)
    }

    public fun save(json: ObjectNode) {
        log.info("save: ${json}")
        bulkUpdate(Jsonifier.idOf(json) as U, Jsonifier.valueMap(json, galaxy.descriptor))
    }
    public fun bulkUpdate(id: U, values: Map<String, Any?>) {
        values.entries.forEach {
            galaxy.setValue(id, it.key, it.value)
        }
    }

    public fun retrieve(ids:Iterable<U>) {
        val session = KISession.current()
        session!!.app.retriever.execute {
            ids.forEach {
                val value = galaxy.get(it)
                if(value!=null) session.entities?.publish(listOf(value as LivingElement<Comparable<Any>>))
            }
        }
    }

    public fun add(id:Int, eid:Comparable<Any>) {
        (KISession.current()!!.interests[id] as? Interest<T,U>)?.add(eid as U)
    }

    public fun remove(id:Int, eid:Comparable<Any>) {
        (KISession.current()!!.interests[id] as? Interest<T,U>)?.remove(eid as U)
    }

    public fun add(eid:Comparable<*>, property:String, target:Comparable<*>) {
        val entity = galaxy.get(eid as U)
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


    public fun refresh(id:Int) {
        KISession.current()?.interests?.get(id)?.refresh()
    }


    public fun clear(id:Int) {
        val ai = KISession.current()!!.interests[id] as? Interest<T, U>
        if(ai!=null) ai.filter = galaxy.filterFactory.staticFilter(ai)
    }

    public fun<W:Comparable<W>> call(id:W, action:String, pars:ArrayNode) : Any? {
        val eid = id as U
        val om = ObjectMapper()
        val a = galaxy.sourceType.exposeds().filter {
            it.name==action
        }.first()!!
        val mpars = a.method.getParameterTypes()!!
        val args = Array<Any?>(mpars.size) {null}
        mpars.forEachIndexed { idx,cl ->
             args[idx] = om.treeToValue<Object?>(pars[idx], cl as Class<Object?>)
        }
        log.info("invoking ${a.name}:${a.method.getReturnType()} with ${args}")
        return a.method.invoke(galaxy.get(eid), *args)
    }
}


public interface EventPublisher {
    fun publish(events: Iterable<Event<Comparable<Any>>>)
}

public interface EntityPublisher {
    fun publish(entities: Iterable<LivingElement<Comparable<Any>>>)
}