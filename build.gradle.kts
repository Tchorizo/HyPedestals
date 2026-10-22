plugins {
    java
}

version = "0.3.2"

base {
    archivesName = "HyPedestals"
}

repositories {
    mavenCentral()
    maven {
        name = "hytaleRelease"
        url = uri("https://maven.hytale.com/release")
    }
}

dependencies {
    // The server API is provided by Hytale at runtime; never bundle it.
    compileOnly("com.hypixel.hytale:Server:0.7.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testCompileOnly("com.hypixel.hytale:Server:0.7.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("manifest.json") {
        expand("version" to pluginVersion)
    }
}

tasks.test {
    useJUnitPlatform()
}

// The ordinary jar task is intentionally flat: Hytale supplies the server API at runtime.
