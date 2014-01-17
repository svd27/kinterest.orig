package ch.passenger.kinterest.util

import java.util.AbstractList
import ch.passenger.kinterest.Interest
import ch.passenger.kinterest.StaticFilter
import rx.subjects.Subject
import ch.passenger.kinterest.Event
import rx.subjects.PublishSubject
import ch.passenger.kinterest.LivingElement
import rx.Observable
import java.util.ArrayList
import ch.passenger.kinterest.DataStore
import ch.passenger.kinterest.UpdateEvent
import ch.passenger.kinterest.Galaxy
import ch.passenger.kinterest.DomainPropertyDescriptor
import ch.passenger.kinterest.Universe

/**
 * Created by svd on 15/12/13.
 */


class EntityList<T:LivingElement<U>,U:Comparable<U>,V:LivingElement<W>,W:Comparable<W>>(val name:String, private val owner:T, private val store:DataStore<Event<U>,U>, private val galaxy:Galaxy<V,W>) : MutableList<V> {
    private val pd : DomainPropertyDescriptor = owner.descriptor().descriptors[name]!!
    private final val content : MutableList<W> = ArrayList();

    {
        store.findRelations<W>(owner.id(), name, owner.descriptor()).subscribe {
            content.add(it!!)
        }
    }

    private fun removeRelation(to:W) {
        store.removeRelation(owner.id(), to, name, owner.descriptor())
    }

    override public fun add(v:V) : Boolean {
        if(content.contains(v.id())) return false
        store.addRelation(owner, v, name, owner.descriptor())
        return true
    }


    override fun remove(o: Any?): Boolean {
        if(o!=null && o is LivingElement<*> && o.id() in content) {
            if(o!!.javaClass.isAssignableFrom(pd.classOf)) {
                removeRelation(o.id() as W)
            }
        }

        return false
    }
    override fun addAll(c: Collection<V>): Boolean {
        return c.fold(true) {(fl, it) -> fl&&add(it)}
    }
    override fun addAll(index: Int, c: Collection<V>): Boolean {
        throw UnsupportedOperationException()
    }
    override fun removeAll(c: Collection<Any?>): Boolean {
        return c.fold(true) { (fl,it) -> fl&&remove(it) }
    }
    override fun retainAll(c: Collection<Any?>): Boolean {
        throw UnsupportedOperationException()
    }
    override fun clear() {
        content.forEach { removeRelation(it) }
    }
    override fun set(index: Int, element: V): V {
        throw UnsupportedOperationException()
    }
    override fun add(index: Int, element: V) {
        throw UnsupportedOperationException()
    }
    override fun remove(index: Int): V {
        val v = galaxy.get(content[index])
        removeRelation(content[index])
        return v!!
    }
    override fun listIterator(): MutableListIterator<V> {
        throw UnsupportedOperationException()
    }
    override fun listIterator(index: Int): MutableListIterator<V> {
        throw UnsupportedOperationException()
    }
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<V> {
        return object : AbstractList<V>() {
            val els = content.subList(fromIndex, toIndex)

            override fun get(index: Int): V = galaxy.get(els[index])!!
            override fun size(): Int = els.size
        }
    }
    override fun size(): Int = content.size
    override fun isEmpty(): Boolean = content.size == 0
    override fun contains(o: Any?): Boolean {
        if(o is LivingElement<*>) return content.contains(o.id())
        return false
    }

    override fun containsAll(c: Collection<Any?>): Boolean {
        throw UnsupportedOperationException()
    }
    override fun get(index: Int): V {
        throw UnsupportedOperationException()
    }
    override fun indexOf(o: Any?): Int {
        throw UnsupportedOperationException()
    }
    override fun lastIndexOf(o: Any?): Int {
        throw UnsupportedOperationException()
    }

    override fun iterator(): MutableIterator<V> {
        return object : MutableIterator<V> {
            val cursor = 0
            override fun remove() {
                removeRelation(content[cursor])
            }
            override fun next(): V {
                return galaxy.get(content[cursor])!!
            }
            override fun hasNext(): Boolean {
                return cursor < size()
            }
        }
    }
    public fun remove(v:V) : Boolean {
        if(!content.contains(v.id())) return false
        store.removeRelation(owner.id(), v.id(), name, owner.descriptor())
        return true
    }

    public fun consume(ev:UpdateEvent<U,W>) {
        if(ev.old!=null) content.remove(ev.old)
        if(ev.value != null && !content.contains(ev.value)) content.add(ev.value)
    }


    override fun hashCode(): Int {
        return content.hashCode()
    }
    override fun equals(other: Any?): Boolean {
        if(other is EntityList<*,*,*,*>) {
            return content.equals(other.content)
        }
        return false
    }

    public fun asInterest(n:String=name) : Interest<V,W> {
        val g = Universe.galaxy<V,W>(galaxy.descriptor.descriptors[name]!!.targetEntity)
        val i = g!!.interested(n)
        content.forEach { i.add(it) }
        return i
    }
}