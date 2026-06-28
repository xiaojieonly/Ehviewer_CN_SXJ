package com.hippo.ehviewer.web

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EhWebApplication

fun main(args: Array<String>) {
    runApplication<EhWebApplication>(*args)
}