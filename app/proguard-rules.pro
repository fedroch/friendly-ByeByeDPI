# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class io.github.fedroch.byedpi.core.ByeDpiProxy { *; }

-keep,allowoptimization class io.github.fedroch.byedpi.core.TProxyService { *; }
-keep,allowoptimization class io.github.fedroch.byedpi.activities.** { *; }
-keep,allowoptimization class io.github.fedroch.byedpi.services.** { *; }
-keep,allowoptimization class io.github.fedroch.byedpi.receiver.** { *; }

-keep class io.github.fedroch.byedpi.fragments.** {
    <init>();
}

-keep,allowoptimization class io.github.fedroch.byedpi.data.** {
    <fields>;
}

-keepattributes Signature
-keepattributes *Annotation*

-repackageclasses 'ru.fedroch'
-renamesourcefileattribute ''
-keepattributes SourceFile,InnerClasses,EnclosingMethod,Signature,RuntimeVisibleAnnotations,*Annotation*,*Parcelable*
-allowaccessmodification
-overloadaggressively
-optimizationpasses 5
-verbose
-dontusemixedcaseclassnames
-adaptclassstrings
-adaptresourcefilecontents **.xml,**.json
-adaptresourcefilenames **.xml,**.json