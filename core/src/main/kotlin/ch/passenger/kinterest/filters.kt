package ch.passenger.kinterest

import java.lang.reflect.Method
import javax.persistence.Entity
import java.util.regex.Pattern
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import java.util.concurrent.TimeUnit
import ch.passenger.kinterest.util.json.EnumDecoder
import java.util.Date
import java.text.DateFormat
import java.text.SimpleDateFormat
import ch.passenger.kinterest.util.firstThat
import org.slf4j.LoggerFactory

/**
 * Created by svd on 14/12/13.
 */
interface ElementFilter<T,U> where T:LivingElement<U>, U:Comparable<U> {
    public fun accept(element:T) : Boolean
    val relation : FilterRelations
    val target : Class<T>

    val kind :String

    fun kind() : String  {
        //log.info("ann: ${target.getAnnotation(javaClass<Entity>())?.name()}")
        val ann = target.getAnnotation(Entity::class.java)?.name
        if(ann!=null&&ann.trim().length()>0) return ann
        return target.name
    }

    fun toJson() : ObjectNode
}

interface  CombinationFilter<T:LivingElement<U>,U:Comparable<U>> : ElementFilter<T,U> {
   val combination : Iterable<ElementFilter<T,U>>

    override fun toJson(): ObjectNode {
       val om = ObjectMapper()
       val json = om.createObjectNode()!!
       json["relation"] = om.valueToTree(relation.name)!!
        val op = om.createArrayNode()!!
        json["operands"] = op
        combination.forEach {
            op.add(it.toJson())
        }
        return json
    }
}


public enum class FilterRelations {EQ, NEQ, NOT, AND, OR, LT, GT, LTE, GTE, IN, LIKE, NOTLIKE, STATIC, FROM, TO}

abstract class PropertyFilter<T,U,V:Comparable<V>>(override val target:Class<T>, val property:String, val value:V) : ElementFilter<T,U> where U : Comparable<U>, T:LivingElement<U>, U:Comparable<U> {
    private final val log = LoggerFactory.getLogger(PropertyFilter::class.java)
    private var method : Method? = null;
    private var _kind :String?= null
    override val kind : String get() {
        if(_kind==null) {
            log.info("PropertFiltery kind: ${kind()}")
            _kind = kind()
        }
        return _kind!!
    }

    override fun accept(element: T): Boolean {
        if(method==null) {
            val target = "get"+property.capitalize()
            for(m in element.javaClass.methods) {
                if(m.name ==target) method=m
                if(method==null && m.name ==property) method = m
            }
            if(method==null) throw IllegalStateException()
        }
        val current = method?.invoke(element) as V
        var av : Any? = null

        //TODO: this kinda casting is really crappy,
        //neccessary coz filters come in via json so proper casting isnt done
        if(current is Number) {
            when(current) {
                is Double -> av = (value as Number).toDouble()
                is Float -> av = (value as Number).toFloat()
                is Int -> av = (value as Number).toInt()
                is Short -> av = (value as Number).toShort()
                is Long -> av = (value as Number).toLong()
            }
        }

        if(current is Date) {
            when(value) {
                is Number -> av = dates.parse("$value")
                is String -> av = dates.parse(value)
                is Date -> av = value
                else -> throw IllegalArgumentException("cant interpret $value as date")
            }
        }

        if(current.javaClass.isEnum) {
            if (!value.javaClass.isEnum)
                if (value is String)
                    av = EnumDecoder.decode(current.javaClass, value)
                else throw IllegalArgumentException("cant interpret $value as enum")
        }

        return compare(current, if(av!=null) av else value)
    }

    protected abstract fun compare(current:V, value:Any?) : Boolean

    private class EQ<T,U:Comparable<U>,V:Comparable<V>>(target:Class<T>,property:String, value:V) : PropertyFilter<T,U,V>(target,property, value) where T:LivingElement<U>  {
        override val relation: FilterRelations = FilterRelations.EQ

        override fun compare(current: V, v:Any?): Boolean = current==v
    }


    override fun toJson(): ObjectNode {
        val om = ObjectMapper()
        val json = om.createObjectNode()!!
        json["relation"] = om.valueToTree(relation.name)
        json["property"] = om.valueToTree(property)
        json["value"] = om.valueToTree(value)
        return json
    }
    companion  object {
        val dates : DateFormat = SimpleDateFormat("yyyyMMddHHmmssSSS")
        public fun <T:LivingElement<U>,U:Comparable<U>,V:Comparable<V>> eq(target:Class<T>,p:String, value:V) : PropertyFilter<T,U,V> = EQ(target, p, value)
    }
}

abstract class RelationFilter<T:LivingElement<U>,U:Comparable<U>>(val property:String, val f:ElementFilter<out LivingElement<out Comparable<Any>>,out Comparable<Any>>) : ElementFilter<T,U> {

}

//class FromFilter<T:LivingElement<U>,U:Hashable>(val ) : RelationFilter()

class FilterFactory<T,U:Comparable<U>>(val galaxy:Galaxy<T,U>, val target:Class<T>, val descriptor:DomainObjectDescriptor) where T : LivingElement<U> {
    private final val log = LoggerFactory.getLogger(FilterFactory::class.java)
    fun<V:Comparable<V>> neq(property:String, value:V) : PropertyFilter<T,U,V> {
        return binrel(property, value, FilterRelations.NEQ) {
            e,v -> if(v==null) false else e.compareTo(v!! as V) != 0
        }
    }
    fun<V:Comparable<V>> eq(property:String, value:V) : PropertyFilter<T,U,V> {
        return PropertyFilter.eq(target, property,value)
    }

    fun<V:Comparable<V>> lt(property:String, value:V) : PropertyFilter<T,U,V> {
        return binrel<V>(property, value, FilterRelations.LT) {
            e,v -> e<(v as V)
        }
    }

    fun<V:Comparable<V>> gt(property:String, value:V) : PropertyFilter<T,U,V> {
        return binrel<V>(property, value, FilterRelations.GT) {
            e,v -> e>(v as V)
        }
    }

    fun<V:Comparable<V>> lte(property:String, value:V) : PropertyFilter<T,U,V> {
        return binrel<V>(property, value, FilterRelations.LTE) {
            e,v -> e<=(v as V)
        }
    }

    fun<V:Comparable<V>> gte(property:String, value:V) : PropertyFilter<T,U,V> {
        return binrel<V>(property, value, FilterRelations.GTE) {
            e,v -> e.compareTo((v as V)) >= 0
        }
    }

    fun like(property:String, value:String) : PropertyFilter<T,U,String> {
        return object : PropertyFilter<T,U,String>(target, property, value) {
            val pat = Pattern.compile(value);
            override fun compare(e:String,v:Any?) = pat.matcher(e).matches()

            override val relation: FilterRelations = FilterRelations.LIKE
        }
    }

    fun notlike(property:String, value:String) : PropertyFilter<T,U,String> {
        return object : PropertyFilter<T,U,String>(target, property, value) {
            val pat = Pattern.compile(value);
            override fun compare(e:String,v:Any?) = !pat.matcher(e).matches()

            override val relation: FilterRelations = FilterRelations.NOTLIKE
        }
    }

    private fun binop(rel:FilterRelations, filters:Iterable<ElementFilter<T,U>>, filter:(T,Iterable<ElementFilter<T,U>>)->Boolean) : ElementFilter<T,U> {
        return object : CombinationFilter<T,U> {
            override fun accept(element: T): Boolean = filter(element, filters)
            override val relation: FilterRelations = rel
            override val target: Class<T> = this@FilterFactory.target
            override val kind: String = kind()

            override val combination: Iterable<ElementFilter<T, U>> = filters
        }
    }

    fun and(vararg f:ElementFilter<T,U>) : ElementFilter<T,U> {
        return binop(FilterRelations.AND, f.toList()) {
            e,fl -> fl.all { it.accept(e) }
        }
    }

    fun or(vararg f:ElementFilter<T,U>) : ElementFilter<T,U> {
        return binop(FilterRelations.OR, f.toList()) {
            e,fl -> fl.any { it.accept(e) }
        }
    }

    fun <V:Comparable<V>> binrel(p:String, v:V, rel:FilterRelations,filter:(e:V,v:Any?)->Boolean) : PropertyFilter<T,U,V> {
        return object : PropertyFilter<T,U,V>(target, p, v) {
            override fun compare(e:V,av:Any?) = filter(e,av)

            override val relation: FilterRelations = rel
        }
    }

    fun not(op:ElementFilter<T,U>) : ElementFilter<T,U> {
        return object : ElementFilter<T,U> {
            override fun accept(element: T): Boolean = !op.accept(element)
            override val relation: FilterRelations = FilterRelations.NOT
            override val target: Class<T> = this@FilterFactory.target
            override val kind: String = kind()

            override fun toJson(): ObjectNode {
                val om = ObjectMapper()
                val json = om.createObjectNode()!!
                json["relation"] = om.valueToTree(FilterRelations.NOT.name())
                json["operand"] = op.toJson()
                return json
            }
        }
    }

    fun from(property:ObjectNode, filter:ElementFilter<LivingElement<out Comparable<Any>>,out Comparable<Any>>) : RelationFilter<T,U> {
        return object : RelationFilter<T,U>(property["property"]!!.textValue()!!, filter) {
            override fun accept(element: T): Boolean {
                val entity = property["entity"]!!.textValue()!!
                val sp = property["property"]!!.textValue()!!
                val tg = Universe.galaxy<LivingElement<Comparable<Any>>,Comparable<Any>>(entity)!!
                val ff = tg.filterFactory


                var tf = filter as ElementFilter<LivingElement<Comparable<Any>>, Comparable<Any>>
                val bf = eq<Comparable<Any>>("id", element.id() as Comparable<Any>) as ElementFilter<LivingElement<out Comparable<Any>>,out Comparable<Any>>
                tf = ff.and(ff.to(sp, bf) , tf)
                val res = tg.filter(tf, arrayOf(), 0, 1).timeout(1000, TimeUnit.MILLISECONDS)!!.toBlocking()!!.toFuture()!!.get()
                return res != null
            }
            override val relation: FilterRelations = FilterRelations.FROM
            override val target: Class<T> = this@FilterFactory.target
            override val kind: String = this@FilterFactory.descriptor.entity
            override fun toJson(): ObjectNode {
                val om = ObjectMapper()
                val json = om.createObjectNode()!!
                json["relation"] = om.valueToTree(FilterRelations.FROM.name())
                json["filter"] = f.toJson()
                return json
            }
        }
    }

    fun to(property:String, filter:ElementFilter<LivingElement<out Comparable<Any>>,out Comparable<Any>>) : RelationFilter<T,U> {
        return object : RelationFilter<T,U>(property, filter) {
            override fun accept(element: T): Boolean {
                val fk = galaxy.getValue(element.id(), property) as LivingElement<Comparable<Any>>?

                if(fk==null) return false
                return filter.accept(fk)
            }

            override val relation: FilterRelations = FilterRelations.TO
            override val target: Class<T> = this@FilterFactory.target
            override val kind: String = this@FilterFactory.descriptor.entity

            override fun toJson(): ObjectNode {
                val om = ObjectMapper()
                val json = om.createObjectNode()!!
                json["relation"] = om.valueToTree(FilterRelations.TO.name())
                json["filter"] = f.toJson()
                return json
            }
        }
    }


    fun staticFilter(i:Interest<T,U>) : ElementFilter<T,U> = StaticFilter(i)

    fun fromJson(json:ObjectNode) : ElementFilter<T,U> {
        val om = ObjectMapper()
        log.debug("FILTER: ${om.writeValueAsString(json)}")
        val sop = json["relation"]!!.textValue()!!
        val op = FilterRelations.valueOf(sop)
        return when(op) {
            FilterRelations.EQ -> {
                val p = json["property"]!!.textValue()!!
                eq(p, resolveProperty(om, p, json.get("value")!!))
            }
            FilterRelations.LT -> {
                val p = json["property"]!!.textValue()!!
                lt(p, resolveProperty(om, p, json.get("value")!!))
            }
            FilterRelations.LTE -> {
                val p = json["property"]!!.textValue()!!
                lte(p, resolveProperty(om, p, json.get("value")!!))
            }
            FilterRelations.GT -> {
                val p = json["property"]!!.textValue()!!
                gt(p, resolveProperty(om, p, json.get("value")!!))
            }
            FilterRelations.GTE -> {
                val p = json["property"]!!.textValue()!!
                gte(p, resolveProperty(om, p, json.get("value")!!))
            }
            FilterRelations.LIKE -> {
                val p = json["property"]!!.textValue()!!
                like(p, json["value"]!!.textValue()!!)
            }
            FilterRelations.GTE -> {
                val p = json["property"]!!.textValue()!!
                gte(p, resolveProperty(om, p, json.get("value")!!))
            }
            FilterRelations.AND -> {
                val operands = json["operands"]!!
                val oa = operands.map { fromJson(it as ObjectNode) }.toTypedArray()
                and(*oa)
            }
            FilterRelations.OR -> {
                val operands = json["operands"]!!
                val oa = operands.map { fromJson(it as ObjectNode) }.toTypedArray()
                or(*oa)
            }
            FilterRelations.NOT -> {
                return not(fromJson(json["operand"]!! as ObjectNode))
            }
            FilterRelations.FROM -> {
                val prop = json["qname"] as ObjectNode
                val source = prop["entity"]!!.textValue()!!
                val sfj = json["filter"]!! as ObjectNode
                sfj["entity"] = om.valueToTree(source)
                val sf = Universe.galaxy<LivingElement<Comparable<Any>>,Comparable<Any>>(source)!!.filterFactory.fromJson(sfj)
                return from(prop, sf as ElementFilter<LivingElement<out Comparable<Any>>, out Comparable<Any>>)
            }
            FilterRelations.TO -> {
                val prop = json["property"]!!.textValue()!!

                return to(prop, relationFilter(json, om, prop))
            }
            else -> throw IllegalArgumentException("can not creat filter from $json")

        }


    }

    fun relationFilter(json:ObjectNode, om:ObjectMapper, prop:String) :  ElementFilter<LivingElement<out Comparable<Any>>, out Comparable<Any>> {
        val prop = json["property"]!!.textValue()!!
        val ff = json["filter"]!! as ObjectNode
        val pd = descriptor.descriptors[prop]!!
        val entity = pd.classOf.entityName()
        ff["entity"] = om.valueToTree(entity)
        return Universe.galaxy<LivingElement<Comparable<Any>>,Comparable<Any>>(entity)!!.filterFactory.fromJson(ff) as  ElementFilter<LivingElement<out Comparable<Any>>, out Comparable<Any>>
    }

    fun resolveProperty(om:ObjectMapper, prop:String, node:JsonNode) : Comparable<Any> {
        val meth = target.methods.firstThat {(it.name =="id"&& prop=="id" ) || it.name =="get${prop.capitalize()}" || it.name =="is${prop.capitalize()}"}
        val cls : Class<*>? = meth?.returnType

        log.info("node $node cls $cls target: $target")
        return om.treeToValue<Any>(node, cls!! as Class<Any>)!! as Comparable<Any>
    }
}

class StaticFilter<T:LivingElement<U>,U:Comparable<U>>(private val interest:Interest<T,U>) : ElementFilter<T,U> {
    override val relation: FilterRelations = FilterRelations.STATIC
    override val target: Class<T> = interest.target
    override val kind: String = interest.sourceType
    override fun accept(element: T): Boolean = interest.contains(element)

    override fun toJson(): ObjectNode {
        val om = ObjectMapper()
        val json = om.createObjectNode()!!
        json["relation"] = om.valueToTree(FilterRelations.STATIC.name())
        return json
    }
}

enum class SortDirection { ASC, DESC }
fun oppositeSortDirection(dir:SortDirection):SortDirection{
    if(dir==SortDirection.ASC) return SortDirection.DESC else return SortDirection.ASC
}
class SortKey(val property:String,val direction:SortDirection)
