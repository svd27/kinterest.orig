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


class EntityList<T:LivingElement<U>,U:Comparable<U>,V:LivingElement<W>,W:Comparable<W>>(val name:String, private val owner:T, private val store:DataStore<Event<U>,U>, private val galaxy:Galaxy<V,W>)  {
    private val pd : DomainPropertyDescriptor = owner.descriptor().descriptors[name]!!

    private fun removeRelation(to:W) {
        store.removeRelation(owner.id(), to, name, owner.descriptor())
    }

    public fun add(v:V)  {
        store.addRelation(owner, v, name, owner.descriptor())
    }

    public fun remove(v:V) {
        removeRelation(v.id())
    }


    public fun size() : Int {
        return store.countRelations<V>(owner.id(), name, owner.descriptor())!!.toBlockingObservable()!!.single()!!
    }

    public fun get(idx:Int) : V? {
        return store.findNthRelations<V>(owner.id(), name, idx, owner.descriptor())!!.toBlockingObservable()!!.singleOrDefault(null)!!
    }
}