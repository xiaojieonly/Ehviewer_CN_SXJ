plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

group = "com.hippo.anotherviewer"
version = (project.findProperty("webVersion") as String?) ?: "1.0.0-SNAPSHOT"

// Generate version.properties from the webVersion project property
// (gradle.properties, overridable via -PwebVersion=<ver>), so runtime
// endpoints (health/metrics) report the same version as the built jar
// instead of a hardcoded constant. The generated file is added to main
// resources below and therefore lands on the runtime + test classpath.
val generateVersionProperties by tasks.registering {
    val versionValue = (project.findProperty("webVersion") as String?) ?: "0.0.0"
    val outputDir = layout.buildDirectory.dir("generated/resources/main")
    inputs.property("anotherviewer.version", versionValue)
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        File(dir, "version.properties").writeText("anotherviewer.version=$versionValue\n")
    }
}

sourceSets["main"].resources.srcDir(
    generateVersionProperties.map { layout.buildDirectory.dir("generated/resources/main") }
)
tasks.named<ProcessResources>("processResources") {
    dependsOn(generateVersionProperties)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":anotherviewer-core"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.hibernate.orm:hibernate-community-dialects:6.6.4.Final")
    implementation("org.springframework.security:spring-security-messaging")
    implementation("com.hierynomus:smbj:0.12.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    runtimeOnly("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}