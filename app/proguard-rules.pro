-keepattributes *Annotation*
-keep,includedescriptorclasses class com.nxteam.nxstore.**$$serializer { *; }
-keepclassmembers class com.nxteam.nxstore.** {
    *** Companion;
}
-keepclasseswithmembers class com.nxteam.nxstore.** {
    kotlinx.serialization.KSerializer serializer(...);
}
