package ch.passenger.kinterest

import java.lang.reflect.Method
import javax.persistence.Entity
import java.util.regex.Pattern

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
}

trait CombinationFilter<T:LivingElement<U>,U:Hashable> : ElementFilter<T,U> {
   val combination : Iterable<ElementFilter<T,U>>
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

    fun<V:Comparable<S>,S:String> like(property:String, value:S) : PropertyFilter<T,U,S> {
        return object : PropertyFilter<T,U,S>(target, property, value) {
            val pat = Pattern.compile(value);
            override fun compare(e:S) = pat.matcher(e).matches()

            override val relation: FilterRelations = FilterRelations.LIKE
        }
    }

    fun<V:Comparable<S>,S:String> notlike(property:String, value:S) : PropertyFilter<T,U,S> {
        return object : PropertyFilter<T,U,S>(target, property, value) {
            val pat = Pattern.compile(value);
            override fun compare(e:S) = !pat.matcher(e).matches()

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
        }
    }
}

class StaticFilter<T:LivingElement<U>,U:Hashable>(private val interest:Interest<T,U>) : ElementFilter<T,U> {
    override val relation: FilterRelations = FilterRelations.STATIC
    override val target: Class<T> = interest.target
    override val kind: String = interest.sourceType
    override fun accept(element: T): Boolean = interest.contains(element)
}

enum class SortDirection { ASC DESC }
fun oppositeSortDirection(dir:SortDirection):SortDirection{
    if(dir==SortDirection.ASC) return SortDirection.DESC else return SortDirection.ASC
}
class SortKey(val property:String,val direction:SortDirection)
