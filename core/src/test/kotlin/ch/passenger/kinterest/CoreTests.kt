package ch.passenger.kinterest

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.record.impl.ODocument
import javax.persistence.*
import org.slf4j.LoggerFactory
import org.junit.Test
import com.orientechnologies.orient.core.db.ODatabase
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.OStorage
import com.orientechnologies.orient.core.sql.OCommandExecutorSQLCreateClass
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal
import rx.subjects.PublishSubject


/**
 * Created by svd on 11/12/13.
 */

Entity
trait TestA :LivingElement<Long> {
    [Id]
    override fun id():Long
    val version : Int [Version] get
    var value : Double
}

class OrientHandle(val url:String, val user:String, val pwd:String, val pool:ODatabaseDocumentPool) {
    fun db() : ODatabaseDocumentTx  {
        val db = pool.acquire(url, user, pwd)!!
        ODatabaseRecordThreadLocal.INSTANCE!!.set(db.getUnderlying()!!)
        return db
    }
}

class TestAImpl(val id:Long, val handle:OrientHandle) : TestA {

    override val subject: PublishSubject<UpdateEvent<Long, Any?>> = PublishSubject.create()!!
    override fun id(): Long = id
    override var value: Double
    set(v) {
        _value = v
        save()
    }
    get() = _value

    private var _value : Double = 0.toDouble()

    override val version : Int
    get() = doc().field<Int>("version")?.toInt()?:-1


    fun save() {
        val d = doc()
        d.field("value", value)
        d.field("meta-class", javaClass<TestA>().getName())
        handle.db().save<ODocument>(d)
    }

    fun doc() : ODocument {
        val res = handle.db().query<List<ODocument>>(OSQLSynchQuery<ODocument>("select from test where ID = ${id}"))!!
        assert(res.size()<2)

        if(res.size()==1) {
            return res[0]
        }
        var doc : ODocument = ODocument()
        doc.field("id", id)
        return doc
    }
    class object {
        fun find(id:Long, h:OrientHandle) : TestA? {
            val res = h.db().query<List<ODocument>>(OSQLSynchQuery<ODocument>("select from test where ID = ${id}"))!!
            return if(res.size()>0) {
                return TestAImpl(res[0].field<Long>("id")!!, h)
            } else null;
        }

        fun create(id:Long, h:OrientHandle) : TestA {
            val res = h.db().query<List<ODocument>>(OSQLSynchQuery<ODocument>("select from test where ID = ${id}"))!!
            if(res.size()>0) throw IllegalStateException()
            val testAImpl = TestAImpl(id, h)
            testAImpl.save()
            return testAImpl
        }
    }
}

class Core() {
    val log = LoggerFactory.getLogger(javaClass<Core>())!!
    Test
    fun testElementProducer() {
        val oh = OrientHandle("memory:test", "admin", "admin", ODatabaseDocumentPool.global()!!)
        val db = ODatabaseDocumentTx("memory:test")
        if(!db.exists())
        db.create<ODatabaseDocumentTx>()
        if(db.isClosed())
        db.open<ODatabaseDocumentTx>("admin", "admin");
        db.addCluster("main",OStorage.CLUSTER_TYPE.MEMORY)
        db.getMetadata()!!.getSchema()!!.createClass("test")
        val cls = javaClass<TestA>()
        log.info("${cls.getName()} ${cls.isInterface()}")
        cls.getAnnotations().forEach {
            log.info(it.toString())
        }

        cls.getDeclaredFields().forEach { log.info(it.getName()); it.getAnnotations().forEach { log.info(it.toString()) } }
        cls.getMethods().forEach { log.info(it.getName()); it.getAnnotations().forEach { log.info(it.toString()) } }
        val ta = TestAImpl.create(1, oh)
        ta.value = 2.2
        assert(ta.value==2.2)
        val iter = oh.db().browseClass("test")
        while(iter?.hasNext()?:false) {
            log.info("iter: ${iter?.next()}")
        }
        val ci = oh.db().browseCluster("main")
        while(ci?.hasNext()?:false) {
            log.info("ci: ${ci?.next()}")
        }
        oh.db().close()
    }
}
