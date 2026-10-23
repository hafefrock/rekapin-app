package com.example.data.model

data class VisualStyle(
    val id: String,
    val name: String,
    val promptDescription: String,
    val isBuiltIn: Boolean = false
)
