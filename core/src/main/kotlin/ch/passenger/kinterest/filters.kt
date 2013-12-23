package ch.passenger.kinterest

import java.lang.reflect.Method
import javax.persistence.Entity
import java.util.regex.Pattern
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode

/**
 * Created by svd on 14/12/13.
 */
trait ElementFilter<T,U> where T:LivingElement<U>, U:Hashable {
    fun accept(element:T) : Boolean
    val relation : FilterRelations
    val target : Class<T>

    val kind :String

    protected fun kind() : String  {
        //log.info("ann: ${target.getAnnotation(javaClass<Entity>())?.name()}")
        val ann = target.getAnnotation(javaClass<Entity>())?.name()
        if(ann!=null&&ann.trim().length()>0) return ann
        return target.getName()
    }

    fun toJson() : ObjectNode
}

trait CombinationFilter<T:LivingElement<U>,U:Hashable> : ElementFilter<T,U> {
   val combination : Iterable<ElementFilter<T,U>>

    override fun toJson(): ObjectNode {
       val om = ObjectMapper()
       val json = om.createObjectNode()!!
       json["relation"] = om.valueToTree(relation.name())!!
        val op = om.createArrayNode()!!
        json["operands"] = op
        combination.forEach {
            op.add(it.toJson())
        }
        return json
    }
}


public enum class FilterRelations {EQ NEQ NOT AND OR LT GT LTE GTE IN LIKE NOTLIKE STATIC}

abstract class PropertyFilter<T,U,V:Comparable<V>>(override val target:Class<T>, val property:String, val value:V) : ElementFilter<T,U> where T:LivingElement<U>, U:Hashable {
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
            for(m in element.javaClass.getMethods()) {
                if(m.getName()==target) method=m
                if(method==null && m.getName()==property) method = m
            }
            if(method==null) throw IllegalStateException()
        }
        val current = method?.invoke(element) as V
        return compare(current)
    }

    protected abstract fun compare(current:V) : Boolean

    private class EQ<T,U:Hashable,V:Comparable<V>>(target:Class<T>,property:String, value:V) : PropertyFilter<T,U,V>(target,property, value) where T:LivingElement<U>  {
        override val relation: FilterRelations = FilterRelations.EQ

        override fun compare(current: V): Boolean = current==value
    }


    override fun toJson(): ObjectNode {
        val om = ObjectMapper()
        val json = om.createObjectNode()!!
        json["relation"] = om.valueToTree(relation.name())
        json["property"] = om.valueToTree(property)
        json["value"] = om.valueToTree(value)
        return json
    }
    class object {
        public fun eq<T:LivingElement<U>,U:Hashable,V:Comparable<V>>(target:Class<T>,p:String, value:V) : PropertyFilter<T,U,V> = EQ(target, p, value)
    }
}

class FilterFactory<T,U:Hashable>(val target:Class<T>) where T : LivingElement<U> {
    fun<V:Comparable<V>> neq(property:String, value:V) : PropertyFilter<T,U,V> {
        return binrel<V>(property, value, FilterRelations.NEQ) {
            (e,v) -> e!=v
        }
    }
    fun<V:Comparable<V>> eq(property:String, value:V) : PropertyFilter<T,U,V> {
        return PropertyFilter.eq<T,U,V>(target, property,value)
    }

    fun<V:Comparable<V>> lt(property:String, value:V) : PropertyFilter<T,U,V> {
        return binrel<V>(property, value, FilterRelations.LT) {
            (e,v) -> e<v
        }
    }

    fun<V:Comparable<V>> gt(property:String, value:V) : PropertyFilter<T,U,V> {
        return binrel<V>(property, value, FilterRelations.GT) {
            (e,v) -> e>v
        }
    }

    fun<V:Comparable<V>> lte(property:String, value:V) : PropertyFilter<T,U,V> {
        return binrel<V>(property, value, FilterRelations.LTE) {
            (e,v) -> e<=v
        }
    }

    fun<V:Comparable<V>> gte(property:String, value:V) : PropertyFilter<T,U,V> {
        return binrel<V>(property, value, FilterRelations.GTE) {
            (e,v) -> e>=v
        }
    }

    fun like(property:String, value:String) : PropertyFilter<T,U,String> {
        return object : PropertyFilter<T,U,String>(target, property, value) {
            val pat = Pattern.compile(value);
            override fun compare(e:String) = pat.matcher(e).matches()

            override val relation: FilterRelations = FilterRelations.LIKE
        }
    }

    fun notlike(property:String, value:String) : PropertyFilter<T,U,String> {
        return object : PropertyFilter<T,U,String>(target, property, value) {
            val pat = Pattern.compile(value);
            override fun compare(e:String) = !pat.matcher(e).matches()

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
            (e,fl) -> fl.all { it.accept(e) }
        }
    }

    fun or(vararg f:ElementFilter<T,U>) : ElementFilter<T,U> {
        return binop(FilterRelations.AND, f.toList()) {
            (e,fl) -> fl.any { it.accept(e) }
        }
    }

    fun binrel<V:Comparable<V>>(p:String, v:V, rel:FilterRelations,filter:(e:V,v:V)->Boolean) : PropertyFilter<T,U,V> {
        return object : PropertyFilter<T,U,V>(target, p, v) {
            override fun compare(e:V) = filter(e,v)

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

    fun fromJson(json:ObjectNode) : ElementFilter<T,U> {
        val om = ObjectMapper()
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
                val oa = operands.map { fromJson(it as ObjectNode) }.copyToArray()
                and(*oa)
            }
            FilterRelations.OR -> {
                val operands = json["operands"]!!
                val oa = operands.map { fromJson(it as ObjectNode) }.copyToArray()
                or(*oa)
            }
            FilterRelations.NOT -> {
                return not(fromJson(json["operand"]!! as ObjectNode))
            }
            else -> throw IllegalArgumentException("can not creat filter from $json")

        }
    }

    fun resolveProperty(om:ObjectMapper, prop:String, node:JsonNode) : Comparable<Any> {
        val cls : Class<*>? = target.getMethods().filter {it.getName()=="id" || it.getName()=="set${prop.capitalize()}" || it.getName()=="is${prop.capitalize()}"}.fold<Method,Class<*>?>(null) {
            (cls, m) -> m.getReturnType()
        }
        return om.treeToValue<Any>(node, cls!! as Class<Any>)!! as Comparable<Any>
    }
}

class StaticFilter<T:LivingElement<U>,U:Hashable>(private val interest:Interest<T,U>) : ElementFilter<T,U> {
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

enum class SortDirection { ASC DESC }
fun oppositeSortDirection(dir:SortDirection):SortDirection{
    if(dir==SortDirection.ASC) return SortDirection.DESC else return SortDirection.ASC
}
class SortKey(val property:String,val direction:SortDirection)
