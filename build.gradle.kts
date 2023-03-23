import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import java.lang.System.getProperty

plugins {
    kotlin("jvm") version("1.8.10")
    id("org.graalvm.buildtools.native") version("0.9.20")
}

val options = "-Xmx48m"
val hexagonVersion = "2.6.6"
val gradleScripts = "https://raw.githubusercontent.com/hexagonkt/hexagon/$hexagonVersion/gradle"

apply(from = "$gradleScripts/kotlin.gradle")
apply(from = "$gradleScripts/application.gradle")
apply(from = "$gradleScripts/native.gradle")

defaultTasks("build")

version="1.0.0"
group="org.example"
description="Service's description"

ext.set("options", options)
ext.set("modules", "java.logging")

extensions.configure<JavaApplication> {
    mainClass.set("org.example.ApplicationKt")
    applicationDefaultJvmArgs = options.split(" ")
}

dependencies {
    "implementation"("com.hexagonkt:http_server_jetty:$hexagonVersion")
    "implementation"("com.hexagonkt:serialization_jackson_json:$hexagonVersion")
    "implementation"("org.slf4j:slf4j-nop:2.0.6")

    "testImplementation"("com.hexagonkt:http_client_jetty:$hexagonVersion")
}

extensions.configure<GraalVMExtension> {
    fun option(name: String, value: (String) -> String): String? =
        getProperty(name)?.let(value)

    binaries {
        named("main") {
            listOfNotNull(
                "--enable-url-protocols=classpath",
                "--initialize-at-run-time=com.hexagonkt.core.NetworkKt",
                "--initialize-at-build-time=com.hexagonkt.core.ClasspathHandler",
                "--static", // Won't work on Windows or macOS
                "-R:MaxHeapSize=16",
                option("enableMonitoring") { "--enable-monitoring" },
            )
            .forEach(buildArgs::add)
        }
    }
}
