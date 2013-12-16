//Generated: 2013-12-16T09:14:33.304+01:00
class TestAImpl(val id:Long, store:ch.passenger.kinterest.neo4j.Neo4jDatastore<Long>, node:org.neo4j.graphdb.Node) : ch.passenger.kinterest.neo4j.Neo4jDomainObject(id, store, TestAImpl.kind,node), ch.passenger.kinterest.testdomain.TestA, ch.passenger.kinterest.LivingElement<Long> {
  override fun id() : Long = id
  override protected val subject = subject()
  
override var ami : String?
get() = prop("ami")
set(v) = prop("ami", v)
override var lots : Double
get() = prop("lots")!!
set(v) = prop("lots", v)
override val weight : ch.passenger.kinterest.testdomain.TestB?
                get() {
                 val oid = TestAImpl.galaxy.relation(id(), javaClass<ch.passenger.kinterest.testdomain.TestB>(), "weight")
                 if(oid!=null) return ch.passenger.kinterest.Universe.get(javaClass<ch.passenger.kinterest.testdomain.TestB>(), oid)
                 return null
                }
override var mustbeset : Double
get() = prop("mustbeset")!!
set(v) = prop("mustbeset", v)
override val name : String
get() = prop("name")!!
                  override val optionalWeights : ch.passenger.kinterest.Interest<ch.passenger.kinterest.testdomain.TestB,Long> =
                  ch.passenger.kinterest.Interest<ch.passenger.kinterest.testdomain.TestB,Long>("", javaClass<ch.passenger.kinterest.testdomain.TestB>())
                
override var options : String
get() = prop("options")!!
set(v) = prop("options", v)
        public fun equals(o :Any?) : Boolean {
        return when(o) {
            is ch.passenger.kinterest.testdomain.TestA ->  id().equals(o.id())
            else -> false
        }
    }

    public fun hashCode() : Int = id.hashCode()
        

  class object {
    val kind : String = "TestA"
    val galaxy : ch.passenger.kinterest.Galaxy<ch.passenger.kinterest.testdomain.TestA,Long> get() = ch.passenger.kinterest.Universe.galaxy(javaClass<ch.passenger.kinterest.testdomain.TestA>())!!
    fun create(id:Long, weight : ch.passenger.kinterest.testdomain.TestB?, mustbeset : Double, name : String ,  store:ch.passenger.kinterest.neo4j.Neo4jDatastore<Long>) : ch.passenger.kinterest.testdomain.TestA {
                val node = store.create(id, kind){
                
it.setProperty("lots",6.6)
if(weight!=null)
                    TestAImpl.galaxy.createRelation(id, "TestB", weight.id(), "weight", true)
                    
it.setProperty("mustbeset",mustbeset)
it.setProperty("name",name)
it.setProperty("options","")
                }
                return TestAImpl(id, store, node)

        }
        fun get(id:Long, store:ch.passenger.kinterest.neo4j.Neo4jDatastore<Long>) : ch.passenger.kinterest.testdomain.TestA? {
        return store.tx {
           var res : ch.passenger.kinterest.testdomain.TestA? = null
            val node = store.node(id, kind)
            if(node!=null) res = TestAImpl(id, store, node)
            res
        }
      }
  }
}

class TestAGalaxy(val neo4j:ch.passenger.kinterest.neo4j.Neo4jDatastore<Long>) : ch.passenger.kinterest.Galaxy<ch.passenger.kinterest.testdomain.TestA,Long>(javaClass<ch.passenger.kinterest.testdomain.TestA>(), neo4j) {
    override fun generateId(): Long = neo4j.nextSequence(kind) as Long
    override fun retrieve(id: Long): ch.passenger.kinterest.testdomain.TestA? = TestAImpl.get(id, neo4j)
    override fun create(values:Map<String,Any?>) : ch.passenger.kinterest.testdomain.TestA {
        val n = neo4j.create(generateId(), kind) {
            values.entrySet().forEach {
                e ->
                if(e.getKey()!="ID" && e.getKey()!="KIND") {
                    it.setProperty(e.getKey(), e.getValue())
                }
            }
        }
        return TestAImpl.get(n.getProperty("ID") as Long, neo4j)!!
    }
}

public fun boostrapTestA(db:ch.passenger.kinterest.neo4j.Neo4jDbWrapper) {
    ch.passenger.kinterest.Universe.register(TestAGalaxy(ch.passenger.kinterest.neo4j.Neo4jDatastore(db)))
}
        
class TestBImpl(val id:Long, store:ch.passenger.kinterest.neo4j.Neo4jDatastore<Long>, node:org.neo4j.graphdb.Node) : ch.passenger.kinterest.neo4j.Neo4jDomainObject(id, store, TestBImpl.kind,node), ch.passenger.kinterest.testdomain.TestB, ch.passenger.kinterest.LivingElement<Long> {
  override fun id() : Long = id
  override protected val subject = subject()
  
override val weight : Double
get() = prop("weight")!!
override val name : String
get() = prop("name")!!
override val comment : String?
get() = prop("comment")
        public fun equals(o :Any?) : Boolean {
        return when(o) {
            is ch.passenger.kinterest.testdomain.TestB ->  id().equals(o.id())
            else -> false
        }
    }

    public fun hashCode() : Int = id.hashCode()
        

  class object {
    val kind : String = "TestB"
    val galaxy : ch.passenger.kinterest.Galaxy<ch.passenger.kinterest.testdomain.TestB,Long> get() = ch.passenger.kinterest.Universe.galaxy(javaClass<ch.passenger.kinterest.testdomain.TestB>())!!
    fun create(id:Long, weight : Double , name : String , comment : String?,  store:ch.passenger.kinterest.neo4j.Neo4jDatastore<Long>) : ch.passenger.kinterest.testdomain.TestB {
                val node = store.create(id, kind){
                
it.setProperty("weight",weight)
it.setProperty("name",name)
it.setProperty("comment",comment)
                }
                return TestBImpl(id, store, node)

        }
        fun get(id:Long, store:ch.passenger.kinterest.neo4j.Neo4jDatastore<Long>) : ch.passenger.kinterest.testdomain.TestB? {
        return store.tx {
           var res : ch.passenger.kinterest.testdomain.TestB? = null
            val node = store.node(id, kind)
            if(node!=null) res = TestBImpl(id, store, node)
            res
        }
      }
  }
}

class TestBGalaxy(val neo4j:ch.passenger.kinterest.neo4j.Neo4jDatastore<Long>) : ch.passenger.kinterest.Galaxy<ch.passenger.kinterest.testdomain.TestB,Long>(javaClass<ch.passenger.kinterest.testdomain.TestB>(), neo4j) {
    override fun generateId(): Long = neo4j.nextSequence(kind) as Long
    override fun retrieve(id: Long): ch.passenger.kinterest.testdomain.TestB? = TestBImpl.get(id, neo4j)
    override fun create(values:Map<String,Any?>) : ch.passenger.kinterest.testdomain.TestB {
        val n = neo4j.create(generateId(), kind) {
            values.entrySet().forEach {
                e ->
                if(e.getKey()!="ID" && e.getKey()!="KIND") {
                    it.setProperty(e.getKey(), e.getValue())
                }
            }
        }
        return TestBImpl.get(n.getProperty("ID") as Long, neo4j)!!
    }
}

public fun boostrapTestB(db:ch.passenger.kinterest.neo4j.Neo4jDbWrapper) {
    ch.passenger.kinterest.Universe.register(TestBGalaxy(ch.passenger.kinterest.neo4j.Neo4jDatastore(db)))
}
        

        public fun boostrapDomain(db:ch.passenger.kinterest.neo4j.Neo4jDbWrapper) {
        boostrapTestA(db)
boostrapTestB(db)

        }
        