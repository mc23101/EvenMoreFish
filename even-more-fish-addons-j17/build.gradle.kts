plugins {
    id("com.oheers.evenmorefish.java-conventions")
}


repositories {
    maven("https://lss233.littleservice.cn/repositories/minecraft")
    maven("https://maven.aliyun.com/repository/jcenter")
    maven("https://maven.aliyun.com/repository/google")
    maven("https://maven.aliyun.com/repository/central")
    maven("https://maven.aliyun.com/repository/gradle-plugin")
    maven("https://repo.auxilor.io/repository/maven-public/")
    maven("https://repo.auxilor.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.spigot.api)
    compileOnly(libs.oraxen)
    compileOnly(libs.ecoitems.api)
    compileOnly(libs.ecoitems.libreforge)
    compileOnly(libs.ecoitems.eco)
    compileOnly(libs.commons.lang3)
    compileOnly(project(":even-more-fish-api"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}
