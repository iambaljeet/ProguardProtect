package app.proguard

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.proguard.crashes.*
import app.proguard.ui.theme.ProguardProtectTheme

data class CrashTest(
    val id: Int,
    val title: String,
    val description: String,
    val trigger: () -> String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val crashTests = listOf(
            CrashTest(1, "ClassNotFoundException", "Class.forName() with dynamic string — R8 can't trace it", ReflectionClassCrash::trigger),
            CrashTest(2, "NoSuchMethodError", "getDeclaredMethod() with string name — R8 renames method", ReflectionMethodCrash::trigger),
            CrashTest(3, "Gson Serialization", "Gson fromJson() — R8 renames class/fields, breaks JSON mapping", SerializationCrash::trigger),
            CrashTest(4, "Enum valueOf", "Dynamic Enum.valueOf() via Class.forName — R8 strips enum", EnumValueOfCrash::trigger),
            CrashTest(5, "Callback Interface", "Dynamic interface impl via Class.forName + newInstance — R8 strips impl", CallbackInterfaceCrash::trigger),
            CrashTest(6, "NoSuchFieldError", "getDeclaredField() with string name — R8 renames field", ReflectionFieldCrash::trigger),
            CrashTest(7, "IllegalAccessError", "Devirtualization: R8 routes interface call to base private method", DevirtualizationCrash::trigger),
            CrashTest(8, "ServiceLoader Pattern", "Dynamic class loading via constructed string — R8 strips class", ServiceLoaderCrash::trigger),
            CrashTest(9, "TypeToken Stripped", "Gson TypeToken loses generic type — R8 strips Signature attribute", GsonTypeTokenCrash::trigger),
            CrashTest(10, "Companion Object", "Companion class renamed by R8 — reflection on \$Companion fails", CompanionReflectionCrash::trigger),
            CrashTest(11, "Sealed Subclasses", "R8 removes sealed subtypes not directly referenced", SealedClassCrash::trigger),
            CrashTest(12, "Annotation Stripped", "R8 strips @Retention(RUNTIME) annotation — getAnnotation() returns null", AnnotationCrash::trigger),
            CrashTest(13, "Parcelable Renamed", "R8 renames Parcelable class — Bundle deserialization fails", ParcelableCrash::trigger),
            CrashTest(14, "JS Interface Stripped", "R8 renames @JavascriptInterface methods — JS bridge calls fail", JavascriptInterfaceCrash::trigger),
            CrashTest(15, "Native Method Renamed", "R8 renames class with JNI native methods — UnsatisfiedLinkError", NativeMethodCrash::trigger),
            CrashTest(16, "WorkManager Stripped", "R8 renames Worker class — WorkManager ClassNotFoundException", WorkManagerCrash::trigger),
            CrashTest(17, "Custom View Stripped", "R8 renames custom View class — InflateException", CustomViewCrash::trigger),
            CrashTest(18, "Kotlin Object INSTANCE", "R8 removes Kotlin object INSTANCE field — NoSuchFieldError", KotlinObjectCrash::trigger),
        )

        setContent {
            ProguardProtectTheme {
                CrashTestScreen(crashTests)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashTestScreen(crashTests: List<CrashTest>) {
    val results = remember { mutableStateMapOf<Int, Pair<Boolean, String>>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ProguardProtect", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("R8/ProGuard Crash Verifier", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    text = "Tap each crash type to verify if R8 causes it at runtime. " +
                        "Red = crash detected (R8 issue). Green = no crash (fixed).",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(crashTests) { test ->
                CrashTestCard(
                    test = test,
                    result = results[test.id],
                    onTrigger = {
                        try {
                            val result = test.trigger()
                            Log.d("ProguardProtect", "Test ${test.id}: $result")
                            results[test.id] = true to result
                        } catch (e: Exception) {
                            Log.e("ProguardProtect", "Test ${test.id} CRASHED", e)
                            results[test.id] = false to "💥 ${e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName}: ${e.cause?.message ?: e.message}"
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CrashTestCard(
    test: CrashTest,
    result: Pair<Boolean, String>?,
    onTrigger: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                result == null -> MaterialTheme.colorScheme.surfaceVariant
                result.first -> Color(0xFFE8F5E9)
                else -> Color(0xFFFFEBEE)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Test ${test.id}: ${test.title}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = test.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                FilledTonalButton(
                    onClick = onTrigger,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Run")
                }
            }

            if (result != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.second,
                    fontSize = 12.sp,
                    color = if (result.first) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    lineHeight = 16.sp
                )
            }
        }
    }
}