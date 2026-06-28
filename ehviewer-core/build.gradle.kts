plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:3.14.7")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("org.ccil.cowan.tagsoup:tagsoup:1.2.1")
    implementation("com.hierynomus:smbj:0.12.0")
    implementation("com.alibaba:fastjson:1.2.83")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.slf4j:slf4j-simple:2.0.9")
}