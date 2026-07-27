package com.hippo.ehviewer.web

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class EhWebApplication

fun main(args: Array<String>) {
    runApplication<EhWebApplication>(*args)
}