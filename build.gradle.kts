plugins {
    `java`
}

group = "cn.quotidietium.balatro"
version = "0.4.39"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 服务端已提供，仅编译期。
    // 锚定最低支持版本 1.19.4（全息 Display/Interaction 实体的引入版本，见 note/tasks/lower-mc-version.md）：
    // 编译期只见 1.19.4 的 API 面，从源头杜绝误用更新版本的方法（Bukkit 向后兼容，产物可跑在 1.19.4+）。
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Adventure 随 paper-api（compileOnly）不进测试类路径；
    // 聊天帮助增强（HoverText）为纯组件逻辑可单测，显式补 Adventure（4.x 二进制兼容，
    // 主源码按 paper-api 1.19.4 携带的 4.13 时代签名编译，测试类路径用新版同样兼容）。
    testImplementation(platform("net.kyori:adventure-bom:4.26.1"))
    testImplementation("net.kyori:adventure-api")
    testImplementation("net.kyori:adventure-text-serializer-legacy")
    testImplementation("net.kyori:adventure-text-serializer-plain")
}

// 统一 UTF-8；用 JDK 25（本机 PATH）+ --release 17 编译，避免 toolchain 触发 JDK 下载。
// 字节码 17 是 1.19.4~1.20.4 服务端的运行要求（1.20.5+ 才需要 Java 21）；
// Java 17 字节码在更高版本服务端照常加载，上限端不受影响。
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
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
