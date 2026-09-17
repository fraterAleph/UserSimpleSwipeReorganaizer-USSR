// Empty on purpose. Plugins are declared per module, which costs one Gradle warning at
// configuration time ("The Kotlin Gradle plugin was loaded multiple times") and buys the
// thing that warning cannot: :core builds and tests on a machine with no Android SDK.
//
// Silencing the warning means putting the Kotlin plugin on the root classpath. The Kotlin
// Android plugin then fails to apply, because its own classes reference AGP types the root
// project cannot see — and putting AGP at the root is exactly what must not happen, since
// AGP resolves only against Google's Maven. The two are mutually exclusive; the warning is
// the cheaper half. See the conditional include in settings.gradle.kts.
