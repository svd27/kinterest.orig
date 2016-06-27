package ch.passenger.kinterest.util.json

import ch.passenger.kinterest.*
import ch.passenger.kinterest.util.EntityList
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

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

    fun<U:Comparable<U>> jsonify(event: Event<U>): ObjectNode {
        val on = om.createObjectNode()!!
        on.put("kind", event.kind.name)
        on.put("sourceType", event.sourceType)
        when(event) {
            is ElementEvent<U> -> serialise(on, event)
            is OrderEvent<U> -> {
                val an = om.createArrayNode()!!
                on.put("interest", event.interest)
                if(event.order==null)
                    throw IllegalStateException("BOOOM")
                log.info("jsonify ${event.order}")
                event.order.forEach {
                    log.info("jsonify $it")
                    if(it==null)
                        throw IllegalStateException("BOOOM")
                    an.add(om.valueToTree<JsonNode>(it))
                }
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

    fun<U:Comparable<U>> serialise(on: ObjectNode, event: ElementEvent<U>) {
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
            is Enum<*> -> vnode.put(it, pv.name)
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
        json.fieldNames().asSequence()!!.filter { it != "id" }.forEach {
            val pd = desc.descriptors[it]
            if(pd==null) throw IllegalArgumentException()
            if(pd.oneToMany) {
                //just ignore those
            } else if(json[it]!!.isNull()) {
                if(pd.nullable) m[it] = null
                else throw IllegalArgumentException("cant set $it to null!!")
            } else if (pd.relation) {
                when(pd.linkType) {
                    Long::class.java -> m[it] = json[it]!!.asLong()
                    Int::class.java -> m[it] = json[it]!!.asInt()
                    java.lang.Long::class.java -> m[it] = json[it]!!.asLong()
                    java.lang.Integer::class.java -> m[it] = json[it]!!.asInt()
                    else -> throw IllegalArgumentException("$it: ${pd.linkType} currently not supported")
                }
            } else {
                when(pd.classOf) {
                    String::class.java -> m[it] = json[it]!!.textValue()
                    Long::class.java -> m[it] = json[it]!!.longValue()
                    Int::class.java -> m[it] = json[it]!!.intValue()

                    Double::class.java -> m[it] = json[it]!!.doubleValue()
                    Float::class.java -> m[it] = json[it]!!.floatValue()
                    Boolean::class.java -> m[it] = json[it]!!.booleanValue()
                    Date::class.java -> m[it] = jsonDate.parse(json[it]!!.textValue())
                    else -> if (pd.classOf?.isEnum()?:false) {
                        m[it] = EnumDecoder.decode(pd.classOf, json[it]!!.textValue())
                    }
                }
            }
            log.info("$it -> ${m[it]}")
        }
        return m
    }

    public fun readDate(s:String) : Date {
        return jsonDate.parse(s)!!
    }

    private fun<K : Any, V : Any?> asMap(it: Iterable<Pair<K, V>>) {
        val m = HashMap<K, V>()
        it.forEach { m += it }
    }

    public fun entity(json: ObjectNode): String = json["entity"]?.toString()!!


}