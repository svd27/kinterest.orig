package ch.passenger.kinterest.ki18n

import ch.passenger.kinterest.LivingElement
import ch.passenger.kinterest.annotations.Index
import ch.passenger.kinterest.annotations.Unique
import ch.passenger.kinterest.util.EntityList
import javax.persistence.Id
import javax.persistence.OneToMany

/**
 * Created by svd on 22/01/2014.
 */
interface I18NCatalog : LivingElement<Long> {
    @Id
    override fun id(): Long
    val name : String @Unique @Index get
    val keys : EntityList<I18NCatalog,Long,I18NKey,Long> @OneToMany(targetEntity=I18NValue::class) get
}

//I18NKey.values <- (locale="de" && variant="de") || (((locale="de" && variant="NONE")) || locale="en")
interface I18NKey : LivingElement<Long> {
    @Id
    override fun id(): Long
    val name : String @Unique @Index get
    val values : EntityList<I18NKey,Long,I18NValue,Long> @OneToMany(targetEntity=I18NValue::class) get
}

interface I18NValue : LivingElement<Long> {
    @Id
    override fun id(): Long
    val locale : String
    val variant : String
    val value : String
}