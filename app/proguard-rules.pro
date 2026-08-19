# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.recomp.gameshub.data.remote.** {
    *** Companion;
    *** INSTANCE;
}
-keep,includedescriptorclasses class com.recomp.gameshub.data.remote.**$$serializer { *; }
-keepclassmembers class com.recomp.gameshub.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.recomp.gameshub.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}