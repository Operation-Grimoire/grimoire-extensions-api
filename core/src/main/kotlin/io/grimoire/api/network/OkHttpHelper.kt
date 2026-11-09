package io.grimoire.api.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The process-wide source client. Android discovers its browser adapter from the
 * wrapper AAR; JVM hosts install a [SourceHttpPlatform] before loading sources.
 * Derive custom clients with [OkHttpClient.newBuilder] to retain the shared pool.
 * Keep this file name: existing extension bytecode calls OkHttpHelperKt directly.
 */
fun defaultOkHttpClient(): OkHttpClient = SourceNetwork.client()

internal fun buildSourceHttpClient(platform: SourceHttpPlatform): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .cookieJar(platform.cookieJar)
        .addInterceptor(UserAgentInterceptor { platform.userAgent })
        .apply { platform.interceptors.forEach(::addInterceptor) }
        .build()
