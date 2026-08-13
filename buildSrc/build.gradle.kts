plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.jooq.codegen)
    implementation(libs.jooq.meta)
    implementation(libs.postgresql)
    implementation(libs.testcontainers.postgresql)
}
