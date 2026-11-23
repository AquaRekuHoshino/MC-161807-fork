plugins {
    id("java-library")
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "net.minecraft"
version = "rd-167498"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val natives: Configuration by configurations.creating
natives.isTransitive = true

dependencies {
    implementation(group = "org.lwjgl.lwjgl", name = "lwjgl", version = "2.9.3")
    implementation(group = "org.lwjgl.lwjgl", name = "lwjgl_util", version = "2.9.3")
    natives(group = "org.lwjgl.lwjgl", name = "lwjgl-platform", version = "2.9.3", classifier = "natives-windows")
    natives(group = "org.lwjgl.lwjgl", name = "lwjgl-platform", version = "2.9.3", classifier = "natives-linux")
    natives(group = "org.lwjgl.lwjgl", name = "lwjgl-platform", version = "2.9.3", classifier = "natives-osx")
}


task("run", JavaExec::class) {
    jvmArgs = listOf("-Dorg.lwjgl.librarypath=${project.projectDir.toPath()}\\run\\natives")
    main = "com.mojang.minecraft.Minecraft"
    classpath = sourceSets["main"].runtimeClasspath
    workingDir("${project.projectDir.toPath()}\\run")
    dependsOn("extractNatives")
}

task("extractNatives", Copy::class) {
    dependsOn(natives)
    from(natives.map { zipTree(it) })
    into("${project.projectDir.toPath()}\\run\\natives")
}

task("extractNativeLibs", Copy::class) {
    dependsOn(natives)
    into("${layout.buildDirectory.get()}/tmp/natives")
    natives.forEach { nativeFile ->
        from(zipTree(nativeFile)) {
            val classifier = if (nativeFile.name.contains("natives-windows")) "windows" else if (nativeFile.name.contains("natives-linux")) "linux" else if (nativeFile.name.contains("natives-osx")) "macosx" else "unknown"
            into(classifier)
        }
    }
}

tasks.shadowJar {
    dependsOn("extractNativeLibs")
    manifest {
        attributes(
            "Manifest-Version" to "1.0",
            "Main-Class" to "com.mojang.minecraft.Minecraft"
        )
    }
    from("${layout.buildDirectory.get()}/tmp/natives") {
        into("natives")
    }
}