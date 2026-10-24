# Navigation type-safe routes and seed models are kotlinx.serialization classes;
# the serialization plugin ships its own keep rules, so nothing extra is needed.

# Tink (behind EncryptedSharedPreferences, see SupabaseProvider) references Error Prone's
# compile-time annotations, which aren't on the runtime classpath. R8 only needs to know
# they're intentionally absent; nothing is kept.
-dontwarn com.google.errorprone.annotations.**
