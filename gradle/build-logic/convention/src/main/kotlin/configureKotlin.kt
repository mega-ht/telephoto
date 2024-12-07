import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureKotlin() {
  tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
      freeCompilerArgs.addAll(
        "-Xcontext-receivers",
        "-Xexpect-actual-classes", // https://youtrack.jetbrains.com/issue/KT-61573
      )

      // Disable K2 until I can find a better way to reference internal members from other modules.
      // https://youtrack.jetbrains.com/issue/KT-67920
      languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
  }

  val targetJdkVersion = "11"
  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(targetJdkVersion))
    }
  }
  extensions.findByType(JavaPluginExtension::class.java)?.apply {
    sourceCompatibility = JavaVersion.toVersion(targetJdkVersion)
    targetCompatibility = JavaVersion.toVersion(targetJdkVersion)
  }
  extensions.findByType(CommonExtension::class.java)?.apply { // For Android modules.
    compileOptions {
      sourceCompatibility = JavaVersion.toVersion(targetJdkVersion)
      targetCompatibility = JavaVersion.toVersion(targetJdkVersion)
    }
  }
}
