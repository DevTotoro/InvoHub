package com.invohub.backend

import com.invohub.backend.support.TestcontainersConfiguration
import org.springframework.boot.fromApplication
import org.springframework.boot.with

/** Runs the app against Testcontainers instead of docker compose: `./gradlew bootTestRun`. */
fun main(args: Array<String>) {
    fromApplication<Application>().with(TestcontainersConfiguration::class).run(*args)
}
