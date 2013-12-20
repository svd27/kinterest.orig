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

/**
 * Created by svd on 18/12/13.
 *
 */

public open class KIPrincipal(val name:String, val token:String) : Principal {
    override fun getName(): String? = name

    override fun equals(obj: Any?): Boolean {
        if(obj is KIPrincipal) return token == obj.token
        return false
    }
    override fun hashCode(): Int = token.hashCode()

    class object {
        val ANONYMOUS = KIPrincipal("guest", "")
    }
}

public open class KISession(val principal:KIPrincipal)



public class ServiceDescriptor(val service : Class<Service>) {
}

public open class Service(name:String) {}

public open class InterestService<T:LivingElement<U>,U:Hashable>(name:String,val galaxy:Galaxy<T,U>) : Service(name) {
    private val log = LoggerFactory.getLogger(this.javaClass)!!
    public fun create(name:String=""): Int = galaxy.interested(name).id
    public fun query(id:Int, filter:ElementFilter<T,U>) : Unit {galaxy.interests[id]?.filter = filter}
    public fun createElement(values : Map<String,Any?>) : Unit {
        galaxy.create(values)
    }

    public fun save(json:ObjectNode) {
        log.info("save: ${json}")
        bulkUpdate(Jsonifier.idOf(json) as U, Jsonifier.valueMap(json, galaxy.descriptor))
    }
    public fun bulkUpdate(id:U,values : Map<String,Any?>) {
        val el = galaxy.get(id)!!
        val dd= galaxy.descriptor
        values.entrySet().forEach {
            dd.set(el, it.key, it.value)
        }
    }
}