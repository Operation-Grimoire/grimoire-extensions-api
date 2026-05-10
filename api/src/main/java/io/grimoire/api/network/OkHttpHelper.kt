package io.grimoire.api.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
