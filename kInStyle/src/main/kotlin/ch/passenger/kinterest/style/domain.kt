package ch.passenger.kinterest.style

import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.util.EntityList
import javax.persistence.Entity
import ch.passenger.kinterest.annotations.Index
import javax.persistence.UniqueConstraint
import javax.persistence.Id
import javax.persistence.OneToMany
import ch.passenger.kinterest.Universe
import ch.passenger.kinterest.annotations.Expose
import ch.passenger.kinterest.annotations.Label
import com.fasterxml.jackson.databind.node.ObjectNode
import ch.passenger.kinterest.ElementFilter
import ch.passenger.kinterest.SortKey
import ch.passenger.kinterest.SortDirection

/**
 * Created by svd on 18/01/2014.
 */


[Entity(name="CSSStylesheet")]
public trait CSSStylesheet : LivingElement<Long> {
    Id
    override fun id(): Long
    val name : String [UniqueConstraint Index Label] get
    val rules : EntityList<CSSStylesheet,Long,CSSStyleRule,Long> [OneToMany(targetEntity=javaClass<CSSStyleRule>())] get
    [Expose] fun addRule(selector:String, styles:String) {
        val gr = Universe.galaxy<CSSStyleRule,Long>("CSSStyleRule")!!
        val gp = Universe.galaxy<CSSProperty,Long>("CSSProperty")!!
        val rid = gr.create(mapOf("selector" to selector))
        val rule = gr.get(rid)!!
        rules.add(rule)
        styles.split(";").forEach {
            if(it.trim().length>0) {
                val pv = it.split(":")
                if(pv.size==2) {
                    val id = gp.create(mapOf("name" to pv[0], "value" to pv[1], "role" to pv[0]))
                    val p = gp.get(id)!!
                    rule.properties.add(p)
                }
            }
        }
    }

    [Expose] fun getRules(property:Array<Long>) : Array<Long> {
        val gr = Universe.galaxy<CSSStyleRule,Long>("CSSStyleRule")!!
        val gp = Universe.galaxy<CSSProperty,Long>("CSSProperty")!!
        if(property.size==0) return Array(0) {0.toLong()}
        var subf : ElementFilter<LivingElement<out Comparable<Any>>,out Comparable<Any>> = gp.filterFactory.eq("ID", property[0] as Comparable<Any>) as ElementFilter<LivingElement<out Comparable<Any>>,out Comparable<Any>>
        if(property.size>1) {
            val fa = Array<ElementFilter<CSSProperty,Long>>(property.size) {
                gp.filterFactory.eq("ID", property[it] as Comparable<Any>)
            }
            subf = gp.filterFactory.or(*fa) as ElementFilter<LivingElement<out Comparable<Any>>,out Comparable<Any>>
        }
        val f = gr.filterFactory.to("properties", subf)
        val res = gr.filter(f, array(SortKey("id", SortDirection.ASC)), 0, 0).toList()!!.toBlockingObservable()!!.single()!!

        return Array(res.size()) {
            res[it].id()
        }
    }
}

[Entity(name="CSSStyleRule")]
public trait CSSStyleRule : LivingElement<Long> {
    Id
    override fun id(): Long
    val selector : String [Label Index] get
    val properties : EntityList<CSSStyleRule,Long,CSSProperty,Long> [OneToMany(targetEntity=javaClass<CSSProperty>())] get
    [Expose] fun getCSS() : String {
        return properties.map { "${it.name}: ${it.value};" }.makeString(" ", "$selector {", "}")
    }

    [Expose] fun addProperty(name:String, value:String) : Long {
        val gp = Universe.galaxy<CSSProperty,Long>("CSSProperty")
        val id = gp!!.create(mapOf("name" to name, "value" to value))
        properties.add(gp.get(id)!!)
        return id
    }
}

[Entity(name="CSSProperty")]
public trait CSSProperty : LivingElement<Long> {
    Id
    override fun id(): Long
    var role : String [Label Index] get
    val name : String [Label Index] get
    var value : String
}