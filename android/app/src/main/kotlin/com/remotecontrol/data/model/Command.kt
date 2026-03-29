package com.remotecontrol.data.model

data class Command(
    val id: String,
    val name: String,
    val command: String,
    val description: String?,
    val category: String,
    val icon: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class CreateCommand(
    val name: String,
    val command: String,
    val description: String? = null,
    val category: String,
    val icon: String? = null,
)

data class UpdateCommand(
    val name: String? = null,
    val command: String? = null,
    val description: String? = null,
    val category: String? = null,
    val icon: String? = null,
)
