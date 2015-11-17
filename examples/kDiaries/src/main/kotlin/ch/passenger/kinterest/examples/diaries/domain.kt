package ch.passenger.kinterest.examples.diaries

import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.annotations.Index
import ch.passenger.kinterest.annotations.Label
import ch.passenger.kinterest.annotations.Unique
import ch.passenger.kinterest.util.EntityList
import java.util.*
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.OneToOne

/**
 * Created by svd on 16/12/13.
 */
@Entity(name="Diary")
interface Diary : LivingElement<Long> {
    @Id
    override fun id(): Long
    val owner : DiaryOwner @OneToOne(targetEntity=DiaryOwner::class) get
    val title : String @Label get
    val created : Date
    var published : Boolean
}

@Entity(name="DiaryEntry")
interface DiaryDayEntry : LivingElement<Long> {
    @Id
    override fun id(): Long
    var title : String @Label get
    val created : Date
    var dated : Date
    val diary : Diary @OneToOne(targetEntity=Diary::class) get
    var content : String
}

enum class OwnerState {
    ONLINE, OFFLINE
}

@Entity(name="DiaryOwner")
interface DiaryOwner : LivingElement<Long> {
    @Id
    override fun id(): Long
    val email : String @Unique get
    var nick : String @Index @Label get
    var birthdate : Date?
    var state : OwnerState
    var height : Double
    val strength : Int
    var editor : DiaryOwner? @OneToOne(targetEntity=DiaryOwner::class) get
    val buddies : EntityList<DiaryOwner,Long,DiaryOwner,Long> @OneToMany(targetEntity=DiaryOwner::class) get
}

