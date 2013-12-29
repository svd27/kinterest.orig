package ch.passenger.kinterest.util.json

import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.DomainObjectDescriptor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.HashMap
import org.slf4j.LoggerFactory

/**
 * Created by svd on 18/12/13.
 */
public object Jsonifier {
    private val log = LoggerFactory.getLogger(this.javaClass)!!
    val om : ObjectMapper = ObjectMapper()
    fun jsonify( value:LivingElement<*>,  desc:DomainObjectDescriptor, props:Iterable<String>) : ObjectNode {
        val json = om.createObjectNode()!!
        json.put("entity", desc.entity)
        val id = value.id()
        when(id) {
            is Number -> json.put("id", id.toLong())
            //really weird, seems String is not hashable
            //is Object -> json.put("id", id.toString())
            else -> throw IllegalArgumentException("cant serialise id ${id} of type ${id.javaClass}")
        }
        val vnode = om.createObjectNode()!!

        props.forEach {
            println("json $it ${it.javaClass}")
            val pv = desc.get(value, it)
            setValue(vnode, it, pv)
        }
        json.put("values", vnode)
        return json
    }

    public fun setValue(vnode:ObjectNode, it:String, pv:Any?) {
        when(pv) {
            is Double -> vnode.put(it, pv)
            is Float -> vnode.put(it, pv)
            is Number -> vnode.put(it, pv.toLong())
            is Boolean -> vnode.put(it, pv)
            is Enum<*> -> vnode.put(it, pv.name())
            is String -> vnode.put(it, pv)
            is LivingElement<*> -> {
                val id = pv.id()
                if(id is Number) vnode.put(it, id.toLong())
            }
        }
    }

    public fun idOf(json:ObjectNode) : Any {
        //TODO: propert cast to id type
        return json["id"]!!.longValue()
    }

    public fun valueMap(entityNode:ObjectNode, desc:DomainObjectDescriptor) : Map<String,Any?> {
        val m : MutableMap<String,Any?> = HashMap()
        val json = entityNode.get("values")!!
        json.fieldNames()!!.filter { it!="id" }!!.forEach {
            val pd = desc.descriptors[it]
            if(pd!=null && pd.relation) {
                when(pd.linkType) {
                    javaClass<Long>() -> m[it] = json[it]!!.asLong()
                    javaClass<Int>() -> m[it] = json[it]!!.asInt()
                    else -> throw IllegalArgumentException("$it: ${pd.linkType} currently not supported")
                }
            } else {
                when(pd?.classOf) {
                    javaClass<String>() -> m[it] = json[it]!!.textValue()
                    javaClass<Long>() -> m[it] = json[it]!!.longValue()
                    javaClass<Int>() -> m[it] = json[it]!!.intValue()

                    javaClass<Double>() -> m[it] = json[it]!!.doubleValue()
                    javaClass<Float>() -> m[it] = json[it]!!.floatValue()
                    javaClass<Boolean>() -> m[it] = json[it]!!.booleanValue()
                    else -> if(pd?.classOf?.isEnum()?:false) {
                        m[it] = EnumDecoder.decode(pd?.classOf, json[it]!!.textValue())
                    }
                }
            }
            log.info("$it -> ${m[it]}")
        }
        return m
    }

    private fun<K:Any,V:Any?> asMap(it:Iterable<Pair<K,V>>) {
        val m = HashMap<K,V>()
        it.forEach { m.putAll(it) }
    }

    public fun entity(json:ObjectNode) : String = json["entity"]?.toString()!!


}