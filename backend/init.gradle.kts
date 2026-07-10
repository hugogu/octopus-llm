allprojects {
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        mavenCentral()
    }
    buildscript {
        repositories {
            maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
            mavenCentral()
        }
    }
}
settingsEvaluated {
    pluginManagement {
        repositories {
            maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
            gradlePluginPortal()
        }
    }
}
