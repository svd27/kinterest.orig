package ch.passenger.kinterest.util.swing

import ch.passenger.kinterest.Interest
import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.UpdateEvent
import ch.passenger.kinterest.service.InterestService
import ch.passenger.kinterest.util.json.Jsonifier
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import javax.persistence.Id
import javax.persistence.Transient
import javax.swing.table.AbstractTableModel

/**
 * Created by svd on 16/12/13.
 */
class InterestTableModel<T:LivingElement<U>,U:Comparable<U>>(val interest:Interest<T,U>, val service:InterestService<T,U>) : AbstractTableModel() {
    private val log = LoggerFactory.getLogger(InterestTableModel::class.java)!!
    val columns : MutableList<String> = ArrayList();
    val colmap : MutableMap<String,PropertyColumn<T,U>> = HashMap();
    fun columnAt(i:Int) : PropertyColumn<T,U>? {
        return colmap[columns[i]]
    }

    init {
        interest.target.getMethods().forEach {
            if(Modifier.isPublic(it.getModifiers())) {
                val annid : Id? = it.getAnnotation(Id::class.java)
                val trans = it.getAnnotation(Transient::class.java)
                if(trans==null && (annid!=null || it.name!!.startsWith("get"))) {
                    val prop = if(annid!=null && !it.name!!.startsWith("get")) it.name!! else it.name!!.substring(3).decapitalize()
                    var setter : Method? = null
                    val sname = "set${prop.capitalize()}"
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
                    fireTableDataChanged()
                }
            }
        }
    }
    override fun getColumnClass(columnIndex: Int): Class<out Any?> {
        val rt = columnAt(columnIndex)?.getter?.getReturnType()
        when(rt) {
            Date::class.java -> return Date::class.java
        }
        return super<AbstractTableModel>.getColumnClass(columnIndex)
    }


    override fun getColumnName(column: Int): String {
        return columns[column]
    }

    fun get(i:Int) = interest.at(i)
    fun get(id:U) = interest.get(id)


    override fun getRowCount(): Int  {log.info("rowcount ${interest.currentsize}"); return interest.currentsize}
    override fun getColumnCount(): Int = columns.size
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = colmap[columns[columnIndex]]!!.value(this.get(rowIndex))

    fun column(property:String) : PropertyColumn<T,U>? = colmap[property]


    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = column(columns[columnIndex])?.setter!=null


    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val col = column(columns[columnIndex])!!
        val json = Jsonifier.jsonify(interest.at(rowIndex)!! as LivingElement<Comparable<Any>>, interest.descriptor, listOf(col.property))
        Jsonifier.setValue(json.get("values") as ObjectNode, col.property, aValue)
        service.save(json)
        //col?.setter?.invoke(get(rowIndex), aValue)
    }


}

class PropertyColumn<T:LivingElement<U>,U:Comparable<U>>(var name:String, val property:String, val getter:Method, val setter:Method?) {
    fun value(v:T) : Any? = getter.invoke(v)
}