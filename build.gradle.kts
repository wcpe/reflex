import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "1.8.22" apply false
    id("org.tabooproject.shrinkingkt") version "1.0.6" apply false
}

// 当前 fork HEAD 的短哈希（7 位），用于拼出发布版本号 <基础版本>-<短哈希>，与 taboolib「每个提交一版」的命名保持一致；
// 取不到（非 git 环境等）时退化为 unknown，不阻塞构建。
val gitShortHash: String = runCatching {
    ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
        .directory(rootDir).start()
        .inputStream.bufferedReader().use { it.readText() }.trim()
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: "unknown"

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.tabooproject.shrinkingkt")

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        "implementation"(kotlin("stdlib"))
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.8.1")
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.8.1")
    }

    java {
        withSourcesJar()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-XDenableSunApiLintControl"))
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.freeCompilerArgs += listOf("-module-name", "${project.group}.${project.name}")
    }

    publishing {
        repositories {
            maven("https://maven.wcpe.top/repository/maven-tabooproject-release/") {
                credentials {
                    username = project.findProperty("tabooprojectUsername").toString()
                    password = project.findProperty("tabooprojectPassword").toString()
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
            mavenLocal()
        }
        publications {
            create<MavenPublication>("maven") {
                // 版本号：<基础版本>-<7 位短哈希>，与 taboolib 命名一致
                version = "${project.version}-$gitShortHash"
                from(components.findByName("java"))
                println("> Apply \"$groupId:$artifactId:$version\"")
            }
        }
    }
}