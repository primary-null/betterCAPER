-keep class com.bettercaper.api.model.** { *; }
-keep class net.schmizz.sshj.** { *; }
-dontwarn net.schmizz.sshj.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn sun.security.x509.X509Key

# Keep generic signatures for Gson's TypeToken
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Keep Gson library classes
-keep class com.google.gson.** { *; }

# Keep data and model classes that are serialized/deserialized
-keep class com.bettercaper.data.** { *; }

