# Add project specific ProGuard rules here.
-keep class org.fourthline.cling.** { *; }
-keep class org.upnp.** { *; }

# Keep model classes
-keep class org.fourthline.cling.model.** { *; }
-keep class org.upnp.model.** { *; }

# Kotlin
-keep class kotlin.Metadata { *; }
