package ch.passenger.kinterest.annotations


/**
 * Created by svd on 12/12/13.
 */
annotation @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.PROPERTY) class  DefaultValue(val value : String)
annotation @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.PROPERTY) class  Label()
annotation @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.PROPERTY) class  Index
annotation @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.PROPERTY) class  Unique
annotation @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FUNCTION) class  Expose