package com.uj.nextplease

import com.uj.nextplease.security.SecurityProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(SecurityProperties::class)
class NextPleaseApplication

fun main(args: Array<String>) {
    runApplication<NextPleaseApplication>(*args)
}
