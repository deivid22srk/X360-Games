package com.x360games.archivedownloader.data

data class UserCredentials(
    val email: String,
    val password: String
)

data class LoginState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val cookies: String? = null
)
