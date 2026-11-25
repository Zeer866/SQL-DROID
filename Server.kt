package com.mysql.app

data class Server(
    val id: String,
    val name: String,
    val url: String,
    val user: String,
    val pass: String,
    val autoLogin: Boolean
)
