plugins {
    kotlin("jvm") version "1.9.20"
    application
}

group = "info.skyblond"
version = System.getenv("release_tag") ?: "dev"

repositories {
    mavenCentral()
}

dependencies {
    // kotlin logging and logback
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    // ktorm and sqlite
    implementation("org.jetbrains.exposed:exposed-core:0.44.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    implementation("org.xerial:sqlite-jdbc:3.44.0.0")
    // cli things
    implementation("com.github.ajalt.clikt:clikt:4.2.1")
    // json
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
//    executableDir = "usr/local/bin"
    executableDir = ""
    applicationName = "oam"
}
