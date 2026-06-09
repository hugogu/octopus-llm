package com.octopusllm.model

import org.springframework.data.jpa.repository.JpaRepository

interface ModelDefinitionRepository : JpaRepository<ModelDefinition, String> {
    fun findByIsActiveTrue(): List<ModelDefinition>
    fun findByProviderIdAndIsActiveTrue(providerId: String): List<ModelDefinition>
    fun findByIdAndIsActiveTrue(id: String): ModelDefinition?
}
