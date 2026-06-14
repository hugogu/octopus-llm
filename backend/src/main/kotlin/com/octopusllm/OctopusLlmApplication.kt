package com.octopusllm

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class OctopusLlmApplication

fun main(args: Array<String>) {
    runApplication<OctopusLlmApplication>(*args)
}
