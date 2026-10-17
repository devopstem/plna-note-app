# PLNA — keep JNI + Room + Firebase
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class by.smartplanogram.plnanote.nativebridge.** { *; }
-keep class by.smartplanogram.plnanote.data.local.** { *; }
-dontwarn com.google.firebase.**
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
