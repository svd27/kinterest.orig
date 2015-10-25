package ch.passenger.kinterest.annotations


import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Created by svd on 12/12/13.
 */
annotation @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) class  DefaultValue(val value : String)
annotation @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) class  Label()
annotation @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) class  Index
annotation @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) class  Unique
annotation @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) class  Expose