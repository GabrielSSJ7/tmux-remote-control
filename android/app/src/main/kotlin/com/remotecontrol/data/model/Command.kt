package com.remotecontrol.data.model

data class Command(
    val id: String,
    val name: String,
    val command: String,
    val description: String? = null,
    val category: String = "general",
    val icon: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class CreateCommand(
    val name: String,
    val command: String,
    val description: String? = null,
    val category: String = "general",
    val icon: String? = null,
)
