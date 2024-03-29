package ch.passenger.kinterest

import org.junit.Test
import org.slf4j.LoggerFactory
import rx.subjects.PublishSubject
import java.util.*

/**
 * Created by svd on 13/12/13.
 */

enum class FTColors() : Comparable<FTColors> {red, green, blue}
class FTestA(val id:Long, val color:FTColors) : LivingElement<Long> {
    override val subject: PublishSubject<UpdateEvent<Long, Any?>> = PublishSubject.create()!!
    override fun id(): Long = id

    override fun galaxy(): Galaxy<FTestA,Long> = Universe.galaxy<FTestA,Long>("FTestA")!!
    override fun descriptor(): DomainObjectDescriptor {
        throw UnsupportedOperationException()
    }
}

fun<T:LivingElement<U>,U:Comparable<U>> Iterable<T>.filter(f:ElementFilter<T,U>) : Iterable<T> {
    return filter { f.accept(it) }
}

class FilterTests {
    private val log = LoggerFactory.getLogger(FilterTests::class.java)!!
    @Test
    fun eqneq() {
        val l : MutableList<FTestA> = ArrayList(10)
        for(i in 0..10) {
            var c = FTColors.red
            if(i%2==0) c = FTColors.blue
            if(i>0&&i%3==0) c = FTColors.green
            l.add(FTestA(i.toLong(), c))
        }
        l.forEach { log.info("${it.id} ${it.color}") }
        val reds = l.map { if(it.color==FTColors.red) 1 else 0 }.foldRight(0) {m,n -> m+n}
        val greens = l.map { if(it.color==FTColors.green) 1 else 0 }.foldRight(0) {m,n -> m+n}
        val blues = l.map { if(it.color==FTColors.blue) 1 else 0 }.foldRight(0) {m,n -> m+n}
        log.info("red: $reds greens: $greens blues: $blues ")
        assert(reds==3)
        assert(greens==3)
        assert(blues==5)
        val ffac = FilterFactory(FTestA(-1, FTColors.blue).galaxy(), FTestA::class.java, DomainObjectDescriptor(FTestA::class.java))
        val lb = l.filter(ffac.eq("color", FTColors.blue))
        assert(lb.toList().size==blues)
        val lr = l.filter(ffac.eq("color", FTColors.red))
        assert(lr.toList().size==reds)
        val lg = l.filter(ffac.eq("color", FTColors.green))
        assert(lg.toList().size==reds)
        val lnb = l.filter(ffac.neq("color", FTColors.blue))
        assert(lnb.toList().size==reds+greens)
    }

    @Test
    fun rels() {
        val l : MutableList<FTestA> = ArrayList(10)
        for(i in 0..10) {
            var c = FTColors.red
            if(i%2==0) c = FTColors.blue
            if(i>0&&i%3==0) c = FTColors.green
            l.add(FTestA(i.toLong(), c))
        }
        l.forEach { log.info("${it.id} ${it.color}") }
        val reds = l.map { if(it.color==FTColors.red) 1 else 0 }.foldRight(0) {m,n -> m+n}
        val greens = l.map { if(it.color==FTColors.green) 1 else 0 }.foldRight(0) {m,n -> m+n}
        val blues = l.map { if(it.color==FTColors.blue) 1 else 0 }.foldRight(0) {m,n -> m+n}
        log.info("red: $reds greens: $greens blues: $blues ")
        assert(reds==3)
        assert(greens==3)
        assert(blues==5)
        val ffac = FilterFactory(FTestA(-1, FTColors.blue).galaxy(), FTestA::class.java, DomainObjectDescriptor(FTestA::class.java))
        val llt = l.filter(ffac.lt("id", 5.toLong()))
        llt.forEach { log.info("${it.id} ${it.color}")
            assert(it.id<5) }
    }
}