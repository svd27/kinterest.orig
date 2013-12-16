package ch.passenger.kinterest.testdomain
/**
 * Created by svd on 12/12/13.
 */

import ch.passenger.kinterest.annotations.DefaultValue
import ch.passenger.kinterest.annotations.DefaultsTo
import javax.persistence.Id
import ch.passenger.kinterest.LivingElement
import javax.persistence.Entity
import ch.passenger.kinterest.LivingElement
import javax.persistence.SequenceGenerator
import javax.persistence.OneToOne
import javax.persistence.OneToMany
import java.util.Date
import ch.passenger.kinterest.Interest

Entity(name="TestA")
trait TestA : LivingElement<Long> {
    [Id SequenceGenerator(name="galaxy")]
    override fun id(): Long

    val name : String

    var options : String [DefaultValue("\u0022\u0022")] get
    var ami : String?
    var mustbeset : Double
    var lots : Double [DefaultValue("6.6")] get
    val weight : TestB? [OneToOne(targetEntity=javaClass<TestB>())] get
    val optionalWeights : Interest<TestB,Long> [OneToMany(targetEntity=javaClass<TestB>())] get
}

trait TestAComment : LivingElement<Long> {
    [Id SequenceGenerator(name="galaxy")]
    override fun id(): Long
    val title : String
    val content : String
    val date : Date
    val commentator : TestACommentator [OneToOne(targetEntity=javaClass<TestACommentator>())] get
}

trait TestACommentator : LivingElement<Long> {
    [Id SequenceGenerator(name="galaxy")]
    override fun id(): Long
    val title : String
    val content : String
    val date : Date
}


Entity(name = "TestB")
trait TestB : LivingElement<Long> {
    [Id SequenceGenerator(name="galaxy")]
    override fun id() : Long
    val name : String
    val weight : Double [DefaultValue("1.0")] get
    val comment : String?
}
