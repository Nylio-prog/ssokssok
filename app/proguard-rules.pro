# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nylio.ssokssok.**$$serializer { *; }
-keepclassmembers class com.nylio.ssokssok.** { *** Companion; }
-keepclasseswithmembers class com.nylio.ssokssok.** { kotlinx.serialization.KSerializer serializer(...); }
