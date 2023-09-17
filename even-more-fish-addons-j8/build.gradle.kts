plugins {
    id("com.oheers.evenmorefish.java-conventions")
}

repositories {
    maven("https://lss233.littleservice.cn/repositories/minecraft")
    maven("https://maven.aliyun.com/repository/jcenter")
    maven("https://maven.aliyun.com/repository/google")
    maven("https://maven.aliyun.com/repository/central")
    maven("https://maven.aliyun.com/repository/gradle-plugin")
    maven("https://maven.citizensnpcs.co/repo")
}
dependencies {
    compileOnly(libs.spigot.api)
    compileOnly(libs.commons.lang3)
    compileOnly(libs.itemsadder.api)
    compileOnly(libs.denizens.api)
    compileOnly(libs.headdatabase.api)

    compileOnly(project(":even-more-fish-api"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}
