package com.octopusllm.userconfig

import com.octopusllm.auth.User
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_preferences")
class UserPreference(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    @Column(name = "last_selected_model_id", length = 255)
    var lastSelectedModelId: String? = null,

    @Column(name = "theme_preference", length = 50)
    var themePreference: String = "system",

    @Column(name = "sidebar_collapsed", nullable = false)
    var sidebarCollapsed: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
