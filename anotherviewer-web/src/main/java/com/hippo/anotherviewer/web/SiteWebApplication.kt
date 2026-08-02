package com.hippo.anotherviewer.web

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SiteWebApplication

fun main(args: Array<String>) {
    runApplication<SiteWebApplication>(*args)
}