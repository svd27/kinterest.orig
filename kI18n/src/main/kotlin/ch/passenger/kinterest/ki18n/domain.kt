package ch.passenger.kinterest.ki18n

import ch.passenger.kinterest.LivingElement
import javax.persistence.*
import ch.passenger.kinterest.util.EntityList
import ch.passenger.kinterest.annotations.Index

/**
 * Created by svd on 22/01/2014.
 */
trait I18NCatalog : LivingElement<Long> {
    Id
    override fun id(): Long
    val name : String [UniqueConstraint Index] get
    val keys : EntityList<I18NCatalog,Long,I18NKey,Long> [OneToMany(targetEntity=javaClass<I18NValue>())] get
}

//I18NKey.values <- (locale="de" && variant="de") || (((locale="de" && variant="NONE")) || locale="en")
trait I18NKey : LivingElement<Long> {
    Id
    override fun id(): Long
    val name : String [UniqueConstraint Index] get
    val values : EntityList<I18NKey,Long,I18NValue,Long> [OneToMany(targetEntity=javaClass<I18NValue>())] get
}

trait I18NValue : LivingElement<Long> {
    Id
    override fun id(): Long
    val locale : String
    val variant : String
    val value : String
}