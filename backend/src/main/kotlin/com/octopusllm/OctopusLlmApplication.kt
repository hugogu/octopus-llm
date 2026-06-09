package com.octopusllm

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OctopusLlmApplication

fun main(args: Array<String>) {
    runApplication<OctopusLlmApplication>(*args)
}
