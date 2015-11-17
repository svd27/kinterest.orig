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
import ch.passenger.kinterest.annotations.Unique
import ch.passenger.kinterest.util.EntityList

/**
 * Created by svd on 16/12/13.
 */
@Entity(name="Diary")
interface  Diary : LivingElement<Long> {
    @Id
    override fun id(): Long
    val owner : DiaryOwner @OneToOne(targetEntity= DiaryOwner::class) get
    @Label
    val title : String
    @DefaultValue("java.util.Date()")
    val created : Date
}

@Entity(name="DiaryEntry")
interface  DiaryDayEntry : LivingElement<Long> {
    @Id
    override fun id(): Long

    @Label
    var title : String  get
    @DefaultValue("java.util.Date()")
    val created : Date  get
    @DefaultValue("java.util.Date()")
    var dated : Date  get
    val diary : Diary @OneToOne(targetEntity= Diary::class) get
    @DefaultValue("\"type some text\"")
    var content : String  get
}

enum class OwnerState {
    ONLINE, OFFLINE
}

@Entity(name="DiaryOwner")
interface  DiaryOwner : LivingElement<Long> {
    @Id
    override fun id(): Long
    @Unique
    val email : String
    @Index @Label
    var nick : String
    var birthdate : Date?
    var state : OwnerState
    var height : Double
    val strength : Int
    var editor : DiaryOwner? @OneToOne(targetEntity= DiaryOwner::class) get
    val buddies : EntityList<DiaryOwner,Long,DiaryOwner,Long> @OneToMany(targetEntity= DiaryOwner::class) get
}

