# --- Anthropic Java SDK: Jackson-based (de)serialisation via reflection -------------------------
-keep class com.anthropic.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.ext.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# --- OkHttp / Okio ---------------------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- kotlinx.serialization -------------------------------------------------------------------------
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.talkto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Shizuku: the provider is instantiated by the system; hidden API calls are reflective ---------
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class org.lsposed.hiddenapibypass.HiddenApiBypass { public static *; }
-dontwarn org.lsposed.hiddenapibypass.**
-dontwarn android.app.IActivityManager**

# --- Accessibility service referenced from XML ----------------------------------------------------
-keep class com.talkto.app.apps.TalktoAccessibilityService { *; }

# --- Structured outputs in the Anthropic SDK build JSON schemas through java.lang.reflect.Annotated*,
#     which Android does not have. ZnaiKo never takes that path (its tool schemas are written by hand).
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedArrayType
-dontwarn java.lang.reflect.AnnotatedWildcardType
-dontwarn java.lang.reflect.AnnotatedTypeVariable
