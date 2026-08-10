plugins {
    `java`
}

group = "cn.quotidietium.balatro"
version = "0.2.7"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 服务端已提供，仅编译期
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 统一 UTF-8；用 JDK 25（本机 PATH）+ --release 21 编译，避免 toolchain 触发 JDK 21 下载
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val projectVersion = project.version
    inputs.property("version", projectVersion)
    filesMatching("plugin.yml") {
        expand("version" to projectVersion)
    }
}

// 产物命名：balatro-<version>.jar
tasks.jar {
    archiveBaseName.set("balatro")
    archiveClassifier.set("")
}
