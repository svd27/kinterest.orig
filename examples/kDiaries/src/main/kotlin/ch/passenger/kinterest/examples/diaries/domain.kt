package ch.passenger.kinterest.examples.diaries

import ch.passenger.kinterest.LivingElement
import java.util.Date
import ch.passenger.kinterest.annotations.DefaultValue
import javax.persistence.OneToOne
import javax.persistence.OneToMany
import javax.persistence.Entity
import javax.persistence.Id
import org.neo4j.graphdb.GraphDatabaseService
import ch.passenger.kinterest.Universe
import javax.persistence.UniqueConstraint
import ch.passenger.kinterest.annotations.Index
import ch.passenger.kinterest.annotations.Label
import ch.passenger.kinterest.util.EntityList

/**
 * Created by svd on 16/12/13.
 */
Entity(name="Diary")
trait Diary : LivingElement<Long> {
    Id
    override fun id(): Long
    val owner : DiaryOwner [OneToOne(targetEntity=javaClass<DiaryOwner>())] get
    val title : String [Label] get
    val created : Date [DefaultValue("java.util.Date()")] get
}

Entity(name="DiaryEntry")
trait DiaryDayEntry : LivingElement<Long> {
    Id
    override fun id(): Long
    var title : String [Label] get
    val created : Date [DefaultValue("java.util.Date()")] get
    var dated : Date [DefaultValue("java.util.Date()")] get
    val diary : Diary [OneToOne(targetEntity=javaClass<Diary>())] get
    var content : String [DefaultValue("\"type some text\"")] get
}

enum class OwnerState {
    ONLINE OFFLINE
}

Entity(name="DiaryOwner")
trait DiaryOwner : LivingElement<Long> {
    Id
    override fun id(): Long
    val email : String [UniqueConstraint] get
    var nick : String [Index Label] get
    var birthdate : Date?
    var state : OwnerState
    var height : Double
    val strength : Int
    var editor : DiaryOwner? [OneToOne(targetEntity=javaClass<DiaryOwner>())] get
    val buddies : EntityList<DiaryOwner,Long,DiaryOwner,Long> [OneToMany(targetEntity=javaClass<DiaryOwner>())] get
}

