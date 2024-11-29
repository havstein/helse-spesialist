import com.expediagroup.graphql.plugin.gradle.config.GraphQLScalar
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLIntrospectSchemaTask

val testcontainersVersion = "1.20.2"
val graphQLKotlinVersion = "8.1.0"
val ktorVersion = "2.3.12"

plugins {
    id("com.expediagroup.graphql") version "8.1.0"
}

dependencies {
    api("com.nimbusds:nimbus-jose-jwt:9.37.3")
    api("io.ktor:ktor-server-double-receive:$ktorVersion")
    implementation(project(":spesialist-felles"))
    implementation("com.expediagroup:graphql-kotlin-ktor-server:$graphQLKotlinVersion")

    implementation(libs.bundles.logging)
    implementation(libs.jackson.datatype)
    implementation(libs.jackson.helpers)
    implementation(libs.ktor.micrometer)
    api(libs.micrometer.prometheus)

    testImplementation(testFixtures(project(":spesialist-felles")))
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
}

val graphqlDir = "${project.projectDir}/src/main/resources/graphql"

graphql {
    client {
        // hvis endpoint settes her vil spleis introspectes hver gang man bygger
        schemaFile = file("$graphqlDir/schema.graphql")
        // Ved å sette opp UUID her vil koden som genereres for spleis-typene bruke UUID
        customScalars =
            listOf(
                GraphQLScalar("UUID", "java.util.UUID", "no.nav.helse.spesialist.api.graphql.schema.converter.UUIDScalarConverter"),
                GraphQLScalar(
                    "LocalDateTime",
                    "java.time.LocalDateTime",
                    "no.nav.helse.spesialist.api.graphql.schema.converter.LocalDateTimeScalarConverter",
                ),
                GraphQLScalar(
                    "LocalDate",
                    "java.time.LocalDate",
                    "no.nav.helse.spesialist.api.graphql.schema.converter.LocalDateScalarConverter",
                ),
                GraphQLScalar(
                    "YearMonth",
                    "java.time.YearMonth",
                    "no.nav.helse.spesialist.api.graphql.schema.converter.YearMonthScalarConverter",
                ),
            )
        queryFileDirectory = graphqlDir
        packageName = "no.nav.helse.spleis.graphql"
        serializer = com.expediagroup.graphql.plugin.gradle.config.GraphQLSerializer.JACKSON
    }
}

val graphqlIntrospectSchema by tasks.getting(GraphQLIntrospectSchemaTask::class) {
    endpoint.set("https://spleis-api.intern.dev.nav.no/graphql/introspection")
}

val copySchemaFile by tasks.registering(Copy::class) {
    from(graphqlIntrospectSchema.outputFile)
    into(graphqlDir)
}

tasks.graphqlIntrospectSchema {
    finalizedBy(copySchemaFile)
}
