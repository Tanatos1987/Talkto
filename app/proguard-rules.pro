# --- Anthropic Java SDK ------------------------------------------------------------------------------
# The SDK ships its own R8 rules (META-INF/proguard/anthropic-java-core.pro): the Jackson-annotated constructors and
# members of its models, TypeReference, kotlin.reflect and kotlin.Metadata. That is all its reflection needs, so the
# rest of its ~12,600 classes (APIs ZnaiKo never calls) can be removed and renamed. A blanket keep here used to hold
# the whole SDK and was most of the app's code that R8 could not touch.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
# Jackson itself has no rules of its own and loads parts of itself by name; it stays whole (it is small next to the SDK).
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.ext.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

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

# --- ML Kit: its parts are registered by class name in the manifest and made with their no-argument constructor.
#     AGP 9's stricter R8 no longer keeps that constructor for a bare "-keep class" in ML Kit's own rules, so 1.1.94
#     crashed on start (FaceDetection.getClient found no face detector).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# --- Accessibility service referenced from XML ----------------------------------------------------
-keep class com.talkto.app.apps.TalktoAccessibilityService { *; }

# --- Structured outputs in the Anthropic SDK build JSON schemas through java.lang.reflect.Annotated*,
#     which Android does not have. ZnaiKo never takes that path (its tool schemas are written by hand).
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedArrayType
-dontwarn java.lang.reflect.AnnotatedWildcardType
-dontwarn java.lang.reflect.AnnotatedTypeVariable

# --- Everything R8 may rename goes into one package: smaller and harder to read back. -------------
-repackageclasses
