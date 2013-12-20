package ch.passenger.kinterest.util.swing

import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.Interest
import javax.swing.table.AbstractTableModel
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import javax.persistence.Id
import java.util.ArrayList
import java.util.HashMap
import ch.passenger.kinterest.AddEvent
import ch.passenger.kinterest.UpdateEvent
import javax.persistence.Transient
import org.slf4j.LoggerFactory
import ch.passenger.kinterest.ElementEvent
import ch.passenger.kinterest.util.json.Jsonifier
import ch.passenger.kinterest.service.InterestService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Created by svd on 16/12/13.
 */
class InterestTableModel<T:LivingElement<U>,U:Hashable>(val interest:Interest<T,U>, val service:InterestService<T,U>) : AbstractTableModel() {
    private val log = LoggerFactory.getLogger(javaClass())!!
    val columns : MutableList<String> = ArrayList();
    val colmap : MutableMap<String,PropertyColumn<T,U>> = HashMap();
    fun columnAt(i:Int) : PropertyColumn<T,U>? {
        return colmap[columns[i]]
    }

    {
        interest.target.getMethods().forEach {
            if(Modifier.isPublic(it.getModifiers())) {
                val annid : Id? = it.getAnnotation(javaClass<Id>())
                val trans = it.getAnnotation(javaClass<Transient>())
                if(trans==null && (annid!=null || it.getName()!!.startsWith("get"))) {
                    val prop = if(annid!=null && !it.getName()!!.startsWith("get")) it.getName()!! else it.getName()!!.substring(3).decapitalize()
                    var setter : Method? = null
                    var sname = "set${prop.capitalize()}"
                    interest.target.getMethods().forEach {
                        if(it.getName()!!.equals(sname)) setter = it
                    }
                    colmap[prop] = PropertyColumn(prop, prop, it, setter)
                    columns.add(prop)
                }
            }
        }

        interest.observable.subscribe {
            when(it) {
                is UpdateEvent<U,*> -> {
                    val idx = interest.indexOf(it.id)
                    val col = columns.indexOf(it.property)
                    if(idx>=0 && col>=0) {
                        log.info("update $idx,$col ${it.old}->${it.value}")
                        fireTableCellUpdated(idx, col)
                    }
                }
                else -> {
                    if(it is ElementEvent<U>) println(Jsonifier.jsonify<T,U>(interest.get(it.id)!!, interest.descriptor).toString())
                    fireTableDataChanged()
                }
            }
        }
    }


    override fun getColumnName(column: Int): String {
        return columns[column]
    }

    fun get(i:Int) = interest.at(i)
    fun get(id:U) = interest[id]


    override fun getRowCount(): Int =  interest.size()
    override fun getColumnCount(): Int = columns.size
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = colmap[columns[columnIndex]]!!.value(this[rowIndex])

    fun column(property:String) : PropertyColumn<T,U>? = colmap[property]


    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = column(columns[columnIndex])?.setter!=null


    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val col = column(columns[columnIndex])!!
        val json = Jsonifier.jsonify(interest.at(rowIndex)!!, interest.descriptor, col.property)
        Jsonifier.setValue(json.get("values") as ObjectNode, col.property, aValue)
        service.save(json)
        //col?.setter?.invoke(get(rowIndex), aValue)
    }


}

class PropertyColumn<T:LivingElement<U>,U:Hashable>(var name:String, val property:String, val getter:Method, val setter:Method?) {
    fun value(v:T) : Any? = getter.invoke(v)
}