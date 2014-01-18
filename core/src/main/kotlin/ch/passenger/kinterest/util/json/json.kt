package ch.passenger.kinterest.util.json

import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.DomainObjectDescriptor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.HashMap
import org.slf4j.LoggerFactory
import java.util.Date
import java.text.DateFormat
import java.text.SimpleDateFormat
import ch.passenger.kinterest.Event
import ch.passenger.kinterest.ElementEvent
import ch.passenger.kinterest.CreateEvent
import ch.passenger.kinterest.UpdateEvent
import ch.passenger.kinterest.Interest
import ch.passenger.kinterest.InterestEvent
import com.fasterxml.jackson.databind.JsonNode
import ch.passenger.kinterest.OrderEvent
import ch.passenger.kinterest.InterestConfigEvent
import ch.passenger.kinterest.Galaxy
import ch.passenger.kinterest.util.EntityList

/**
 * Created by svd on 18/12/13.
 */
public object Jsonifier {
    private val log = LoggerFactory.getLogger(this.javaClass)!!
    val om: ObjectMapper = ObjectMapper()
    fun jsonify(value: LivingElement<Comparable<Any>>, desc: DomainObjectDescriptor, props: Iterable<String>): ObjectNode {
        val json = om.createObjectNode()!!
        json.put("entity", desc.entity)
        val id = value.id()
        json.put("id", om.valueToTree<JsonNode>(id))
        when(id) {
            is Number -> json.put("id", id.toLong())
        //really weird, seems String is not hashable
            is Comparable<*> -> json.put("id", id.toString())
            else -> throw IllegalArgumentException("cant serialise id ${id} of type ${id.javaClass}")
        }
        val vnode = om.createObjectNode()!!

        props.forEach {
            val g = value.galaxy() as Galaxy<LivingElement<Comparable<Any>>, Comparable<Any>>
            val pv = g.getValue(value.id(), it)
            if(desc.descriptors[it]!!.oneToMany) {
                val el = pv as EntityList<*,*,*,*>
                vnode.put(it, el.size())
            }
            else setValue(vnode, it, pv)
        }
        json.put("values", vnode)
        return json
    }

    fun jsonify<U:Comparable<U>>(event: Event<U>): ObjectNode {
        val on = om.createObjectNode()!!
        on.put("kind", event.kind.name())
        on.put("sourceType", event.sourceType)
        when(event) {
            is ElementEvent<U> -> serialise(on, event)
            is OrderEvent<U> -> {
                val an = om.createArrayNode()!!
                on.put("interest", event.interest)
                event.order.forEach { an.add(om.valueToTree<JsonNode>(it)) }
                on.put("order", an)
            }
            is InterestEvent<U> -> {
                on.put("interest", event.interest)
                on.put("id", om.valueToTree<JsonNode>(event.id))
            }
            is InterestConfigEvent<U> -> {
                on.put("interest", event.interest)
                on.put("limit", event.limit)
                on.put("offset", event.offset)
                on.put("estimatedsize", event.estimated)
                on.put("currentsize", event.currentsize)
                val an = om.createArrayNode()!!
                event.orderBy.forEach { an.add(om.valueToTree<JsonNode>(it)) }
                on.put("orderBy", an)
            }
        }

        return on
    }

    fun serialise<U:Comparable<U>>(on: ObjectNode, event: ElementEvent<U>) {
        on.put("id", om.valueToTree<JsonNode>(event.id))
        when(event) {
            is UpdateEvent<U, *> -> {
                setValue(on, "property", event.property)
                setValue(on, "value", event.value)
                setValue(on, "old", event.old)
            }
        }
    }

    public fun setValue(vnode: ObjectNode, it: String, pv: Any?) {
        when(pv) {
            null -> vnode.put(it, null as Long?)
            is Double -> vnode.put(it, pv)
            is Float -> vnode.put(it, pv)
            is Number -> vnode.put(it, pv.toLong())
            is Boolean -> vnode.put(it, pv)
            is Enum<*> -> vnode.put(it, pv.name())
            is String -> vnode.put(it, pv)
            is Date -> vnode.put(it, jsonDate.format(pv))
            is LivingElement<*> -> {
                val id = pv.id()
                if (id is Number) vnode.put(it, id.toLong())
            }
        }
    }

    public fun idOf(json: ObjectNode): Any {
        //TODO: propert cast to id type
        return json["id"]!!.longValue()
    }

    private final var jsonDate: DateFormat = SimpleDateFormat("yyyyMMddHHmmssSSS")

    public fun valueMap(entityNode: ObjectNode, desc: DomainObjectDescriptor): Map<String, Any?> {
        val m: MutableMap<String, Any?> = HashMap()
        val json = entityNode.get("values")!!
        json.fieldNames()!!.filter { it != "id" }.forEach {
            val pd = desc.descriptors[it]
            if(pd==null) throw IllegalArgumentException()
            if(pd.oneToMany) {
                //just ignore those
            } else if(json[it]!!.isNull()) {
                if(pd.nullable) m[it] = null
                else throw IllegalArgumentException("cant set $it to null!!")
            } else if (pd.relation) {
                when(pd.linkType) {
                    javaClass<Long>() -> m[it] = json[it]!!.asLong()
                    javaClass<Int>() -> m[it] = json[it]!!.asInt()
                    javaClass<java.lang.Long>() -> m[it] = json[it]!!.asLong()
                    javaClass<java.lang.Integer>() -> m[it] = json[it]!!.asInt()
                    else -> throw IllegalArgumentException("$it: ${pd.linkType} currently not supported")
                }
            } else {
                when(pd.classOf) {
                    javaClass<String>() -> m[it] = json[it]!!.textValue()
                    javaClass<Long>() -> m[it] = json[it]!!.longValue()
                    javaClass<Int>() -> m[it] = json[it]!!.intValue()

                    javaClass<Double>() -> m[it] = json[it]!!.doubleValue()
                    javaClass<Float>() -> m[it] = json[it]!!.floatValue()
                    javaClass<Boolean>() -> m[it] = json[it]!!.booleanValue()
                    javaClass<Date>() -> m[it] = jsonDate.parse(json[it]!!.textValue())
                    else -> if (pd.classOf?.isEnum()?:false) {
                        m[it] = EnumDecoder.decode(pd.classOf, json[it]!!.textValue())
                    }
                }
            }
            log.info("$it -> ${m[it]}")
        }
        return m
    }

    private fun<K : Any, V : Any?> asMap(it: Iterable<Pair<K, V>>) {
        val m = HashMap<K, V>()
        it.forEach { m.putAll(it) }
    }

    public fun entity(json: ObjectNode): String = json["entity"]?.toString()!!


}