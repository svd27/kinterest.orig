package ch.passenger.kinterest.neo4j

import ch.passenger.kinterest.annotations.DefaultValue
import ch.passenger.kinterest.annotations.Expose
import ch.passenger.kinterest.annotations.Unique
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.jetbrains.annotations.Nullable
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.Writer
import java.lang.reflect.Modifier
import java.util.*
import javax.persistence.*

/**
 * Created by svd on 17/12/13.
 */
class Neo4jGenerator(val file: File, val recurse: Boolean, val target: File, targetPackage:String="") {
    private val log = LoggerFactory.getLogger(Neo4jGenerator::class.java)!!
    private val pool: ClassPool = ClassPool.getDefault()!!;
    private val uniqueConstraints = StringBuilder()
    val trans = mapOf("java.lang.String" to "String", "long" to "Long", "double" to "Double",
            "java.util.List" to "jet.MutableList", "int" to "Int", "java.lang.Long" to "Long",
            "java.lang.Integer" to "Int", "boolean" to "Boolean");
    val domainBuffer = StringBuilder();

    init {
        log.info("Target: ${target.getAbsolutePath()}")
        val fw: Writer = FileWriter(target)

        loadClasses(file)
        fw.use {
            if(targetPackage.isNotEmpty()) {
                fw.write("package $targetPackage")
            }
            fw.write("\n//Generated: ${DateTime()}\n")
            fw.write("\nimport javax.persistence.*\n")
            generate(file, recurse, fw)
            val appendix =
                    """
        public fun boostrapDomain(db:ch.passenger.kinterest.neo4j.Neo4jDbWrapper) {
        $domainBuffer
        }
        """
            fw.append("\n").append(appendix)
            fw.flush()
        }
        log.info("$fw")

    }

    fun loadClasses(from:File) {
        log.info("load classes")
        if (from.isFile() && from.getAbsolutePath().endsWith(".class")) {
            val ctClass = pool.makeClass(FileInputStream(from))!!
            log.info("loaded ${ctClass}")
        } else if (from.isDirectory() && recurse) {
            from.listFiles()?.forEach { loadClasses(it) }
        }
    }

    fun generate(f: File, recurse: Boolean, target: Writer) {
        log.info("generate $f $recurse ${f.isFile()} ${f.getName()} ${f.getName().endsWith(".class")}")
        if (f.isFile() && f.getAbsolutePath().endsWith(".class")) {
            val ctClass = pool.makeClass(FileInputStream(f))!!
            log.info("ctClass: ${ctClass.getName()}")
            ctClass.getAnnotations()!!.forEach { log.info("$it") }
            val entity = ctClass.getAnnotation(Entity::class.java)
            if (entity != null) {
                target.append(output(ctClass))
            }

        } else if (f.isDirectory() && recurse) {
            f.listFiles()?.forEach { generate(it, recurse, target) }
        }

    }


    fun output(cls: CtClass): String {
        val cn = if (cls.getName()!!.indexOf('.') > 0) cls.getName()!!.substring(cls.getName()!!.lastIndexOf('.') + 1)
        else cls.getName()!!
        var label = cls.getName()
        val ean = cls.getAnnotation(Entity::class.java)
        if (ean is Entity && !ean.name.isEmpty()) {
            label = ean.name
        }
        val cimpl = "${cn}Impl"
        val mths = methods(cls)
        var id: Prop? = null
        val mandatory: MutableList<Prop> = ArrayList()
        mths.values.forEach {
            log.info("checking prop: $it")
            if (it.id) {
                id = it
            }
            else if (it.ro && it.defval() == null) {
                log.info("mandatory: $it")
                val b = mandatory.add(it)
            }
        }


        val mandPars = StringBuilder()
        val crtInit = StringBuilder()
        val body = StringBuilder()
        mths.values.forEach {
            assert(it.unique.xor(it.nullable))
            if (!it.id && !it.onetoone && !it.ontomany) {
                body.append("\noverride ")
                if (it.ro) body.append("val ") else body.append("var ")
                body.append(it.name).append(" : ").append(it.kind)
                if (it.nullable) body.append("?")
                body.append("\nget() = prop(\"${it.name}\", descriptor().descriptors[\"${it.name}\"]!!)")
                if (!it.nullable) body.append("!!")
                if (!it.ro) {
                    body.append("\nset(v) = prop(\"${it.name}\", descriptor().descriptors[\"${it.name}\"]!!, v)")
                }
                if (it.ro) {
                    mandPars.append("${it.name} : ${it.kind}${if(it.nullable) '?' else ' '}, ")
                    crtInit.append("\n").append("it.setProperty(\"").append(it.name).append("\",").append(it.name).append(")")
                }
                if (!it.ro) {
                    if (it.defval() == null && !it.nullable) {
                        mandPars.append(it.name).append(" : ").append(it.kind).append(if (it.nullable) "?" else "").append(", ")
                        crtInit.append("\n").append("it.setProperty(\"").append(it.name).append("\",").append(it.name).append(")")
                    } else if (!it.nullable) {
                        crtInit.append("\n").append("it.setProperty(\"").append(it.name).append("\",").append(it.defval()).append(")")
                    }
                }
            }
            if(!it.id && it.onetoone) {
                val entity : Entity = it.ms[0].getReturnType()!!.getAnnotation(Entity::class.java)!! as Entity
                body.append("\noverride ")
                if (it.ro) body.append("val ") else body.append("var ")
                body.append(it.name).append(" : ").append(it.kind)
                var nullreturn = "return null"
                if (it.nullable) {
                    body.append("?")
                } else nullreturn = "throw IllegalStateException()"

                body.append("""
                get() {
                 val oid = ${cn}Impl.galaxy.relation(id(), javaClass<${it.kind}>(), "${it.name}")
                 if(oid!=null) return ch.passenger.kinterest.Universe.get(javaClass<${it.kind}>(), oid)${if(!it.nullable) "!!" else ""}
                 $nullreturn
                }""")

                if (!it.ro) {
                    body.append("""
                    set(v) {
                      val old = ${it.name}
                      ${cn}Impl.galaxy.setRelation(this, v, old, "${it.name}", ${it.nullable})}
                    """)
                }
                if (it.ro) {
                    mandPars.append("${it.name} : ${it.kind}${if(it.nullable) '?' else ' '}, ")
                    if(it.nullable)
                        crtInit.append("\nif(${it.name}!=null)")

                    crtInit.append("""
                    ${cn}Impl.galaxy.createRelation(id, "${entity.name}", ${it.name}.id(), "${it.name}", ${it.nullable})
                    """)
                }
                if (!it.ro) {
                    if (it.defval() == null && !it.nullable) {
                        mandPars.append(it.name).append(" : ").append(it.kind).append(if (it.nullable) "?" else "").append(", ")
                        crtInit.append("""
                        ${cn}Impl.galaxy.createRelation(id, "${entity.name}", ${it.name}.id(), "${it.name}", ${it.nullable})
                        """)
                    } else if (!it.nullable) {
                        throw IllegalStateException("${it.name} cannot generate")
                    }
                }
            }
            if(!it.id && it.ontomany) {
                if(it.nullable || !it.ro) throw IllegalStateException()
                val many : OneToMany = it.ms[0].getAnnotation(OneToMany::class.java)!! as OneToMany

                val target = many.targetEntity!!.java
                var ret : Class<*>? = null
                target.getMethods().forEach {
                    if(it.getAnnotation(Id::class.java)!=null) {
                        if(ret==null)
                        ret = it.getReturnType() as Class<*>

                    }
                }
                var rtype =ret?.getName()


                if(trans.containsKey(rtype)) rtype = trans[rtype]
                body.append("""
                override val ${it.name}: ch.passenger.kinterest.util.EntityList<${cls.getName()},${id!!.kind},${target.getName()},${rtype}>
                  = ch.passenger.kinterest.util.EntityList<${cls.getName()},${id!!.kind},${target.getName()},${rtype}>("${it.name}", this, store, ${target.getName()}Impl.galaxy)
                """)

            }
        }

        val nullables = mths.values.filter { it.nullable }.map { "\"${it.name}\"" }.joinToString(",");
        domainBuffer.append("boostrap${cn}(db)\n")

        body.append("""
        public override fun equals(o :Any?) : Boolean {
        return when(o) {
            is ${cls.getName()} ->  id().equals(o.id())
            else -> false
        }
    }

    public override fun hashCode() : Int = id().hashCode()
        """)

        return """
class ${cn}Impl(val id:${id!!.kind}, store:ch.passenger.kinterest.neo4j.Neo4jDatastore<ch.passenger.kinterest.Event<${id!!.kind}>,${id!!.kind}>, node:org.neo4j.graphdb.Node) : ch.passenger.kinterest.neo4j.Neo4jDomainObject<${id!!.kind}>(id, store, ${cn}Impl.kind,node, ${cn}Impl.galaxy.descriptor), ${cls.getName()}, ch.passenger.kinterest.LivingElement<${id!!.kind}> {
  override fun id() : ${id!!.kind} = id
  override @Transient val subject = subject()
  override  fun galaxy(): ch.passenger.kinterest.Galaxy<${cls.getName()},${id!!.kind}> = ${cn}Impl.galaxy


  override fun descriptor(): ch.passenger.kinterest.DomainObjectDescriptor = galaxy().descriptor
  $body

  companion object {
    val kind : String = "${label}"
    val galaxy : ch.passenger.kinterest.Galaxy<${cls.getName()},${id!!.kind}> get() = ch.passenger.kinterest.Universe.galaxy<${cls.getName()},${id!!.kind}>(kind)!!
        fun get(${id!!.name}:${id!!.kind}, store:ch.passenger.kinterest.neo4j.Neo4jDatastore<ch.passenger.kinterest.Event<${id!!.kind}>,${id!!.kind}>) : ${cls.getName()}? {
        return store.tx {
           var res : ${cls.getName()}? = null
            val node = store.node(${id!!.name}, kind)
            if(node!=null) res = ${cn}Impl(${id!!.name}, store, node)
            res
        }
      }
  }
}

class ${cn}Galaxy(val neo4j:ch.passenger.kinterest.neo4j.Neo4jDatastore<ch.passenger.kinterest.Event<${id!!.kind}>,${id!!.kind}>) : ch.passenger.kinterest.Galaxy<${cls.getName()},Long>(javaClass<${cls.getName()}>(), neo4j) {
    override fun generateId(): ${id!!.kind} = neo4j.nextSequence(kind) as ${id!!.kind}
    override fun retrieve(id: ${id!!.kind}): ${cls.getName()}? = $cimpl.get(id, neo4j)
    override val nullables: Set<String> = setOf($nullables)
}

public fun boostrap${cn}(db:ch.passenger.kinterest.neo4j.Neo4jDbWrapper) {
    ch.passenger.kinterest.Universe.register(${cn}Galaxy(ch.passenger.kinterest.neo4j.Neo4jDatastore(db)))
}
        """


    }


    inner class Prop(val name: String, val ms: Array<CtMethod>) {

        val ro: Boolean get() = ms.size == 1
        fun defval(): String? {
            val dv = ms[0].getAnnotation(DefaultValue::class.java) as DefaultValue?
            return dv?.value
        }
        val id: Boolean = has(Id::class.java)
        val nullable: Boolean = has(Nullable::class.java)

        fun has(ann: Class<*>): Boolean {
            if (ms[0].getAnnotation(ann) != null) return true
            if (ms.size > 1 && ms[1].getAnnotation(ann) != null) return true
            return false
        }

        val kind: String get() {
            val rt = ms[0].getReturnType()!!.getName()!!
            if (trans.containsKey(rt)) return trans[rt]!!
            return rt
        }

        val onetoone : Boolean get()  = ms[0].getAnnotation(OneToOne::class.java)!=null
        val ontomany : Boolean get()  = ms[0].getAnnotation(OneToMany::class.java)!=null
        val unique : Boolean get()  = id || ms[0].getAnnotation(Unique::class.java)!=null

        public override fun toString(): String = "$name: id? $id ro? $ro default: ${defval()} null: $nullable"
        public fun dumpAnn(): String {
            val sb = StringBuilder()
            sb.append(ms[0].getName()).append(": ")
            ms[0].getAnnotations()!!.forEach { sb.append(it).append(" ") }
            return sb.toString()
        }
    }

    fun methods(cls: CtClass): Map<String, Prop> {
        val props: MutableMap<String, Prop> = HashMap()
        cls.getMethods()?.forEach {
            if (!Modifier.isStatic(it.getModifiers()) && !Modifier.isPrivate(it.getModifiers())) {
                if (it.getName()!!.startsWith("get")) {
                    if (it.getAnnotation(Transient::class.java) == null && it.getAnnotation(Expose::class.java) == null) {
                        val capName = it.getName()!!.substring(3)
                        val pn = capName.decapitalize()
                        if (pn != "class") {
                            var setter: CtMethod? = null
                            cls.getMethods()!!.forEach {
                                if (it.getName() == "set${capName}") setter = it
                            }
                            if (setter == null)
                                props[pn] = Prop(pn, arrayOf(it))
                            else props[pn] = Prop(pn, arrayOf(it, setter!!))
                        }
                    }
                } else if(it.getAnnotation(Id::class.java)!=null) {
                    if(!props.containsKey(it.getName()))
                    props[it.getName()!!] = Prop(it.getName()!!, arrayOf(it))
                }
            }
        }

        return props
    }
}
