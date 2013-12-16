package ch.passenger.orientdb.generator

import org.xeustechnologies.jcl.JarClassLoader
import java.io.File
import org.slf4j.LoggerFactory
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import javax.persistence.Entity
import org.reflections.util.FilterBuilder
import org.reflections.util.ClasspathHelper
import java.net.URL
import java.util.ServiceLoader
import java.util.jar.JarInputStream
import java.io.FileInputStream
import javassist.ClassPool
import javassist.CtClass

/**
 * Created by svd on 11/12/13.
 */
val log = LoggerFactory.getLogger("generator")!!
fun main(args: Array<String>) {



    val cp : ClassPool = ClassPool.getDefault()!!
    cp.appendClassPath("./core/target/classes")
    cp.appendClassPath("./core/target/test-classes")

    val root = File("./core/target/test-classes")
    doDir(root, cp)

    /*
    val jcl : JarClassLoader = JarClassLoader()
    val fl = File("./core/target").listFiles { it.getName().endsWith(".jar") }!!
    fl.forEach {
        log.info("$it")
        //jcl.add(JarInputStream(FileInputStream(it)))


    }
    jcl.add(File("./core/target/classes").toURI().toURL())
    jcl.add(File("./core/target/test-classes").toURI().toURL())

    jcl.initialize()
    jcl.getLoadedClasses()!!.values().forEach {
        log.info("${it.getName()}")
    }

    val pckFilter = FilterBuilder()
    val filterBuilder = pckFilter.includePackage("ch.passenger")
    val mutableSet = ClasspathHelper.forClassLoader(jcl)

    mutableSet?.forEach { log.info("$it") }
    val r = Reflections(ConfigurationBuilder().addClassLoader(jcl)!!.filterInputsBy { !it!!.startsWith("ch.passenger") }!!
            .setUrls(mutableSet)!!.build())
    val set = r.getTypesAnnotatedWith(javaClass<Entity>())!!
    log.info("----------------------------------")
    set.forEach { log.info("$it") }\
    */

}

fun doDir(f :File, cp: ClassPool) {
    log.info("dir: ${f}")
    f.listFiles()!!.forEach {
        if(it.isDirectory()) doDir(it, cp)
        if(it.getName().endsWith(".class")) {
            val cls = cp.makeClass(FileInputStream(it))!!
            log.info("$cls")
            cls.getAnnotations()?.forEach {
                log.info("ANN: $it ${it.javaClass}")
                when(it) {
                    is Entity -> {
                        var name = it.name()!!
                        if(name==null || name!!.trim().length()==0) {
                            name = cls.getName()!!
                            if(name.contains(".")) name = name.substring(name.lastIndexOf('.')+1)
                        }
                        log.info("Entity: ${name}")
                        cls.getDeclaredMethods()
                    }
                }

            }
        }
    }
}