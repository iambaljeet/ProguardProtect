# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

## Fix for Crash Type 1: Reflection ClassNotFoundException
#-keep class app.proguard.models.UserProfile { *; }

## Fix for Crash Type 2: Reflection NoSuchMethodError
#-keepclassmembers class app.proguard.models.PaymentProcessor { *; }

## Fix for Crash Type 3: Gson serialization field renaming
#-keep class app.proguard.models.ApiResponse { *; }

## Fix for Crash Type 4: Enum class loaded dynamically
#-keep class app.proguard.models.OrderStatus { *; }

## Fix for Crash Type 5: Callback interface implementation loaded dynamically
#-keep class app.proguard.models.AnalyticsEventHandler { *; }

## Fix for Crash Type 6: Reflection field access
#-keepclassmembers class app.proguard.models.AppConfig { *; }

## Fix for Crash Type 7: R8 devirtualization → IllegalAccessError
## R8 may optimize calls to the private base-class method when a class doesn't override it.
## Fix: Override the method in the subclass, or keep the class hierarchy:
#-keep class app.proguard.models.DataProcessor { *; }
#-keep class app.proguard.models.BaseProcessor { *; }

## Fix for Crash Type 8: ServiceLoader-like dynamic class loading
#-keep class app.proguard.models.PluginRegistry { *; }

## Fix for Case 9: Gson TypeToken generic signature stripped
#-keepattributes Signature
#-keep class * extends com.google.gson.reflect.TypeToken
#-keep class app.proguard.models.ProductCatalog { *; }

## Fix for Case 10: Companion object reflection
#-keep class app.proguard.models.NetworkConfig$Companion { *; }
#-keep class app.proguard.models.NetworkConfig { *; }

## Fix for Case 11: Sealed class subtype reflection
#-keep class app.proguard.models.CheckoutState { *; }
#-keep class app.proguard.models.CheckoutState$* { *; }

## Fix for Case 12: Custom runtime annotation stripping
#-keepattributes *Annotation*
#-keep @interface app.proguard.models.JsonModel { *; }
#-keepclassmembers class ** {
#    @app.proguard.models.JsonModel *;
#}

## Fix for Case 13: Parcelable class renamed by R8
#-keep class app.proguard.models.ShippingAddress { *; }

## Fix for Crash Type 14: JavascriptInterface methods renamed by R8
#-keep class app.proguard.models.WebBridgeHandler { *; }
#-keepclassmembers class app.proguard.models.WebBridgeHandler {
#    @android.webkit.JavascriptInterface <methods>;
#}

## Fix for Crash Type 15: Native/JNI methods — class renamed by R8
#-keepclasseswithmembernames class * {
#    native <methods>;
#}
#-keep class app.proguard.models.ImageProcessor { *; }

## Fix for Crash Type 16: WorkManager Worker renamed by R8
#-keep class * extends androidx.work.Worker {
#    public <init>(android.content.Context, androidx.work.WorkerParameters);
#}
#-keep class * extends androidx.work.CoroutineWorker {
#    public <init>(android.content.Context, androidx.work.WorkerParameters);
#}

## Fix for Crash Type 17: Custom View renamed by R8 → InflateException
#-keep class * extends android.view.View {
#    public <init>(android.content.Context, android.util.AttributeSet);
#}

## Fix for Crash Type 18: Kotlin object INSTANCE field removed by R8
#-keep class app.proguard.models.ConfigManager { *; }
#-keepclassmembers class app.proguard.models.ConfigManager {
#    public static ** INSTANCE;
#}
## Fix for Crash Type 19: Resource removed by aapt2 strict mode shrinkResources
## The fix is in res/raw/keep.xml — add tools:keep="@drawable/promo_banner" to <resources>.
## For reference only (proguard-rules.pro does not control resource shrinking):
#  In res/raw/keep.xml <resources> element: add tools:keep="@drawable/promo_banner"

## Fix for Crash Type 20: Generic Signature attribute stripped by R8
#-keepattributes Signature, InnerClasses, EnclosingMethod
#-keep class app.proguard.crashes.GenericSignatureCrash$UserProfileCallback { *; }

## Fix for Crash Type 21: Activity/component class renamed by R8 → ComponentName fails
#-keep class app.proguard.crashes.DeepLinkActivity { *; }

## Fix for Crash Type 22: Data class copy()/componentN() removed by R8
#-keep class app.proguard.models.PriceRecord { *; }

## Fix for Crash Type 23: Dynamic proxy interface renamed by R8 → IllegalArgumentException
#-keep interface app.proguard.models.ApiCallInterface { *; }

## Fix for Crash Type 24: Resources referenced only via JSON asset files
## aapt2 cannot see resource names inside JSON files with strict shrinkResources mode.
#  In res/raw/keep.xml <resources> element: add tools:keep="@drawable/feature_banner,@drawable/promo_overlay"

## Fix for Crash Type 25: Fragment subclass renamed by R8 → FragmentManager back-stack fails
#-keep class app.proguard.models.ProfileFragment { *; }
## Or keep ALL Fragment subclasses:
#-keep class * extends androidx.fragment.app.Fragment { <init>(); }

## Fix for Crash Type 26: Serializable class renamed → cross-build deserialization fails
#-keep class app.proguard.models.UserSession { *; }
## Also: Add companion object { private const val serialVersionUID = 1L } to UserSession

## Fix for Crash Type 27: ObjectAnimator property setter renamed by R8
#-keep class app.proguard.models.AnimatedMeterView {
#    public void setMeterLevel(float);
#    public float getMeterLevel();
#}

## Fix for Crash Type 28: Inner/nested class reflection — outer class renamed
#-keep class app.proguard.models.TaskDispatcher { *; }
#-keep class app.proguard.models.TaskDispatcher$TaskResult { *; }

## Fix for Crash Type 29: android:onClick handler method renamed by R8
#-keep class app.proguard.crashes.AndroidOnClickCrash {
#    public void handlePaymentSubmit(android.view.View);
#}
