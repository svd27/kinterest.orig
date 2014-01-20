package ch.passenger.kinterest.style.generate

import ch.passenger.kinterest.neo4j.Neo4jGenerator
import java.io.File

/**
 * Created by svd on 16/12/13.
 */
fun main(args: Array<String>) {
    val g = Neo4jGenerator(File("./target/classes"), true, File("./src/main/kotlin/generated.kt" ), "ch.passenger.kinterest.style")


}