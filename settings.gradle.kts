pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Android_CN_OAID（OAID/AAID 采集）及其华为/荣耀广告标识依赖。
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.gzu-liyujiang")
            }
        }
        maven("https://developer.huawei.com/repo/") {
            content {
                includeGroup("com.huawei.hms")
            }
        }
        maven("https://developer.hihonor.com/repo") {
            content {
                includeGroup("com.hihonor.mcs")
            }
        }
    }
}

rootProject.name = "clan"
include(":app")
