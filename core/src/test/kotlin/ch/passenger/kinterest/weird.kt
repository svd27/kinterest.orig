package ch.passenger.kinterest

import org.junit.Test
import rx.subjects.PublishSubject

/**
 * Created by svd on 16/12/13.

class WeirdA(override val id:Long, val b:String) : LivingElement<Long> {

    override val subject: PublishSubject<UpdateEvent<Long, Any?>> = PublishSubject.create()!!
}
class WeirdB(override val id:Long, val x:Double) : LivingElement<Long> {
    override val subject: PublishSubject<UpdateEvent<Long, Any?>> = PublishSubject.create()!!
}


fun<X:LivingElement<Y>,U:LivingElement<V>,Y:Identifier,V:Identifier> concat(a:X,b:U) : String = "${a.id}${b.id}"

fun<X:LivingElement<Y>,U:LivingElement<V>,Y:Identifier,V:Identifier> combine(a:X,b:U) : Pair<Y,V>{
    print("${a.id} and ${b.id}")
    return a.id to b.id
}

class ADoer<X:LivingElement<Y>,Y:Identifier>() {
    fun<U:LivingElement<V>,V:Identifier> concat(a:X,b:U) : String = "${a.id}${b.id}"

    fun<U:LivingElement<V>,V:Identifier> combine(a:X,b:U) : Pair<Y,V>{
        print("${a.id} and ${b.id}")
        return a.id to b.id
    }
}

class TestWeird {
    Test()
    fun test() {
        val a = WeirdA(1,"ta")
        val b = WeirdB(1,1.0/4)
        val p = combine(a,b)
        assert(p.first==1.toLong())
        assert(p.second==1.toLong())
    }

    Test
    fun testDoer() {
        val a = WeirdA(1,"ta")
        val b = WeirdB(1,1.0/4)
        val doer = ADoer<WeirdA,Long>()
        val p = doer.combine(a,b)
        assert(p.first==1.toLong())
        assert(p.second==1.toLong())
    }
}
 */