package ch.passenger.kinterest.util

import java.util.AbstractList
import ch.passenger.kinterest.Interest
import ch.passenger.kinterest.StaticFilter
import rx.subjects.Subject
import ch.passenger.kinterest.Event
import rx.subjects.PublishSubject
import ch.passenger.kinterest.LivingElement
import rx.Observable

/**
 * Created by svd on 15/12/13.
 */
abstract class ObservableList<T:LivingElement<U>,U:Hashable> : AbstractList<T>(), MutableList<T> {
    public abstract val observable : Observable<Event<U>>

    override abstract fun add(e: T): Boolean

    override abstract fun remove(o: Any?): Boolean
}

class InterestList<T:LivingElement<U>,U:Hashable>(protected val interest:Interest<T,U>) : ObservableList<T,U>() {
    override fun size(): Int = interest.size
    override fun get(index: Int): T = interest.at(index)


    override fun add(e: T): Boolean = interest.add(e.id())
    override fun remove(o: Any?): Boolean {
        if(interest.target.isAssignableFrom(o.javaClass)) return interest.remove(o as U)
        return false
    }
    override public val observable: Observable<Event<U>> = interest.observable
}