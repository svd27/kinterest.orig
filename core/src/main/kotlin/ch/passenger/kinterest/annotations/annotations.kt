package ch.passenger.kinterest.annotations


/**
 * Created by svd on 12/12/13.
 */
annotation @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FIELD) class  DefaultValue(val value : String)
annotation @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FIELD) class  Label()
annotation @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FIELD) class  Index
annotation @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FIELD) class  Unique
annotation @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FUNCTION) class  Expose