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

// Pin the Kotlin JVM target to match sourceCompatibility: without it Kotlin
// defaults to its own maximum supported target (e.g. 24 under JDK 26), which
// fails compile with "Inconsistent JVM Target Compatibility" against javac 21.
// Pinning lets the JVM test gates run on any JDK >= 21 without a toolchain.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    // Override Spring Boot 3.4.x BOM-managed versions so Mockito/ByteBuddy
    // understand JDK 26 class files (inline-mock creation fails otherwise,
    // e.g. "Could not modify all classes [interface javax.sql.DataSource]").
    extra["mockito.version"] = "5.20.0"
    extra["byte-buddy.version"] = "1.17.7"

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
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Allow ByteBuddy to instrument class-file versions newer than it was
    // built for (belt-and-braces alongside the version overrides above).
    jvmArgs("-Dnet.bytebuddy.experimental=true")
    // v4 同步改造（WP2/WP3）回归验证：模块全量用例（1400+）共享一个测试 JVM，
    // 默认堆（512m）在 BackupService 的 LZMA2 分片（每次 export 4 线程 × 约 92MB
    // 编码缓冲）上与既有用例累计物叠加时会偶发 OutOfMemoryError——属测试 JVM
    // 内存受限而非语义失败，抬高测试堆消除该抖动（已验证：1.5g 下全量用例不再 OOM）。
    maxHeapSize = "1536m"
}