package ch.passenger.kinterest.annotations


import java.lang.annotation.Retention
import java.lang.annotation.Target
import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy

/**
 * Created by svd on 12/12/13.
 */
annotation [Retention(RetentionPolicy.RUNTIME)] [Target(ElementType.METHOD)] class  DefaultValue(val value : String)
annotation [Retention(RetentionPolicy.RUNTIME)] [Target(ElementType.METHOD)] class  Index