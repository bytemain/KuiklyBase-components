/*
 * Tencent is pleased to support the open source community by making KuiklyBase available.
 * Copyright (C) 2025 Tencent. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tencent.kmm.network.export

import kotlin.math.min
import kotlin.random.Random

data class NetworkQueryParameter(
    val name: String,
    val value: String
)

class NetworkByteStream(
    val contentLength: Long? = null,
    private val readAllBlock: suspend () -> ByteArray,
    private val cancelBlock: (() -> Unit)? = null
) {
    suspend fun readAll(): ByteArray = readAllBlock()

    fun cancel() {
        cancelBlock?.invoke()
    }
}

sealed class NetworkBody {
    open val contentType: String? = null
    open val contentLength: Long? = null
    open val repeatable: Boolean = true

    object Empty : NetworkBody()

    class Bytes(
        val bytes: ByteArray,
        override val contentType: String = VBTransportContentType.BYTE.toString()
    ) : NetworkBody() {
        override val contentLength: Long = bytes.size.toLong()
    }

    class Text(
        val text: String,
        override val contentType: String = "text/plain; charset=utf-8"
    ) : NetworkBody() {
        override val contentLength: Long = text.encodeToByteArray().size.toLong()
    }

    class Json(
        val json: String
    ) : NetworkBody() {
        override val contentType: String = VBTransportContentType.JSON.toString()
        override val contentLength: Long = json.encodeToByteArray().size.toLong()
    }

    class Form(
        val fields: List<Pair<String, String>>
    ) : NetworkBody() {
        override val contentType: String = "application/x-www-form-urlencoded; charset=utf-8"
    }

    class Multipart(
        val parts: List<NetworkMultipartPart>,
        val boundary: String = defaultNetworkMultipartBoundary()
    ) : NetworkBody() {
        override val contentType: String = "multipart/form-data; boundary=$boundary"
        override val repeatable: Boolean = parts.all { it.body.repeatable }
    }

    class Stream(
        val stream: NetworkByteStream,
        override val contentType: String? = null
    ) : NetworkBody() {
        override val contentLength: Long? = stream.contentLength
        override val repeatable: Boolean = false
    }

    class FileRef(
        val path: String,
        override val contentType: String? = VBTransportContentType.BYTE.toString(),
        override val contentLength: Long? = null,
        private val readAllBlock: (suspend (path: String) -> ByteArray)? = null,
        private val cancelBlock: (() -> Unit)? = null
    ) : NetworkBody() {
        override val repeatable: Boolean = readAllBlock != null

        suspend fun readAll(): ByteArray? = readAllBlock?.invoke(path)

        fun cancel() {
            cancelBlock?.invoke()
        }
    }
}

class NetworkMultipartPart(
    val name: String,
    val body: NetworkBody,
    val fileName: String? = null,
    val headers: Map<String, String> = emptyMap()
)

class NetworkRequest(
    var method: VBTransportMethod = VBTransportMethod.GET,
    var url: String = "",
    var path: String = "",
    val query: MutableList<NetworkQueryParameter> = mutableListOf(),
    val headers: MutableMap<String, String> = mutableMapOf(),
    var body: NetworkBody = NetworkBody.Empty,
    val metadata: MutableMap<String, String> = mutableMapOf(),
    var policy: NetworkRequestPolicy = NetworkRequestPolicy()
) {
    fun addQuery(name: String, value: String): NetworkRequest {
        query.add(NetworkQueryParameter(name, value))
        return this
    }

    fun setHeader(name: String, value: String): NetworkRequest {
        headers[name] = value
        return this
    }

    fun resolvedUrl(): String {
        val base = when {
            url.isNotBlank() && path.isNotBlank() -> appendPath(url, path)
            url.isNotBlank() -> url
            else -> path
        }
        if (query.isEmpty()) {
            return base
        }
        val separator = if (base.contains("?")) "&" else "?"
        return base + separator + query.joinToString("&") {
            "${encodeUrlComponent(it.name)}=${encodeUrlComponent(it.value)}"
        }
    }

    fun copyMutable(): NetworkRequest {
        return NetworkRequest(
            method = method,
            url = url,
            path = path,
            query = query.toMutableList(),
            headers = headers.toMutableMap(),
            body = body,
            metadata = metadata.toMutableMap(),
            policy = policy.copyMutable()
        )
    }
}

class NetworkResponseBody(
    val bytes: ByteArray? = null,
    val stream: NetworkByteStream? = null
) {
    fun text(): String? = bytes?.decodeToString()

    fun <T> decodeBytes(decoder: (ByteArray) -> T): T? = bytes?.let(decoder)

    fun <T> decodeText(decoder: (String) -> T): T? = text()?.let(decoder)

    fun cancelStream() {
        stream?.cancel()
    }
}

class NetworkResponse(
    val request: NetworkRequest,
    val statusCode: Int?,
    val headers: Map<String, List<String>>,
    val body: NetworkResponseBody,
    val error: NetworkError? = null,
    val rawResponse: VBTransportBaseResponse? = null,
    val timing: VBTransportElapseStatistics = VBTransportElapseStatistics()
) {
    val isSuccess: Boolean = error == null && (statusCode == null || statusCode < 400)
}

enum class NetworkErrorKind {
    CANCELLED,
    TIMEOUT,
    DNS,
    CONNECT,
    TLS,
    HTTP_STATUS,
    DECODE,
    AUTH,
    UNKNOWN
}

class NetworkError(
    val kind: NetworkErrorKind,
    val message: String,
    val statusCode: Int? = null,
    val cause: Throwable? = null
)

enum class NetworkPriority {
    LOW,
    NORMAL,
    HIGH
}

enum class NetworkDispatcher {
    IO,
    DEFAULT
}

data class NetworkBackoffPolicy(
    val initialDelayMillis: Long = 300,
    val maxDelayMillis: Long = 5_000,
    val multiplier: Double = 2.0,
    val jitterRatio: Double = 0.0
) {
    fun delayForAttempt(attempt: Int): Long {
        var delay = initialDelayMillis.toDouble()
        repeat(attempt) {
            delay *= multiplier
        }
        val capped = min(delay, maxDelayMillis.toDouble())
        if (jitterRatio <= 0.0) {
            return capped.toLong()
        }
        val jitter = capped * jitterRatio
        return (capped - jitter + Random.nextDouble() * jitter * 2).toLong()
    }
}

data class NetworkRetryPolicy(
    val maxRetries: Int = 0,
    val retryStatusCodes: Set<Int> = setOf(408, 429, 500, 502, 503, 504),
    val retryErrorKinds: Set<NetworkErrorKind> = setOf(
        NetworkErrorKind.TIMEOUT,
        NetworkErrorKind.DNS,
        NetworkErrorKind.CONNECT,
        NetworkErrorKind.TLS,
        NetworkErrorKind.UNKNOWN
    ),
    val backoff: NetworkBackoffPolicy = NetworkBackoffPolicy()
) {
    fun shouldRetry(response: NetworkResponse): Boolean {
        val status = response.statusCode
        if (status != null && status in retryStatusCodes) {
            return true
        }
        val errorKind = response.error?.kind
        return errorKind != null && errorKind in retryErrorKinds
    }
}

data class NetworkRequestPolicy(
    val timeoutMillis: Long = 0,
    val retry: NetworkRetryPolicy = NetworkRetryPolicy(),
    val priority: NetworkPriority = NetworkPriority.NORMAL,
    val dispatcher: NetworkDispatcher = NetworkDispatcher.IO
) {
    fun copyMutable(): NetworkRequestPolicy {
        return NetworkRequestPolicy(
            timeoutMillis = timeoutMillis,
            retry = retry,
            priority = priority,
            dispatcher = dispatcher
        )
    }
}

internal class NetworkBodyBytes(
    val bytes: ByteArray?,
    val contentType: String?,
    val error: NetworkError? = null
)

internal suspend fun NetworkBody.toBytes(): NetworkBodyBytes {
    return when (this) {
        NetworkBody.Empty -> NetworkBodyBytes(null, null)
        is NetworkBody.Bytes -> NetworkBodyBytes(bytes, contentType)
        is NetworkBody.Text -> NetworkBodyBytes(text.encodeToByteArray(), contentType)
        is NetworkBody.Json -> NetworkBodyBytes(json.encodeToByteArray(), contentType)
        is NetworkBody.Form -> NetworkBodyBytes(
            fields.joinToString("&") {
                "${encodeUrlComponent(it.first)}=${encodeUrlComponent(it.second)}"
            }.encodeToByteArray(),
            contentType
        )
        is NetworkBody.Multipart -> multipartBodyBytes(this)
        is NetworkBody.Stream -> NetworkBodyBytes(stream.readAll(), contentType)
        is NetworkBody.FileRef -> {
            val bytes = readAll()
            if (bytes == null) {
                NetworkBodyBytes(
                    null,
                    contentType,
                    NetworkError(
                        kind = NetworkErrorKind.UNKNOWN,
                        message = "FileRef body requires a readAllBlock on this engine."
                    )
                )
            } else {
                NetworkBodyBytes(bytes, contentType)
            }
        }
    }
}

internal fun NetworkBody.cancel() {
    when (this) {
        is NetworkBody.Stream -> stream.cancel()
        is NetworkBody.FileRef -> this.cancel()
        is NetworkBody.Multipart -> parts.forEach { it.body.cancel() }
        else -> Unit
    }
}

private suspend fun multipartBodyBytes(body: NetworkBody.Multipart): NetworkBodyBytes {
    val builder = VBTransportMultipartBodyBuilder(body.boundary)
    body.parts.forEach { part ->
        val bodyBytes = part.body.toBytes()
        bodyBytes.error?.let {
            return NetworkBodyBytes(null, body.contentType, it)
        }
        builder.addPart(
            name = part.name,
            bytes = bodyBytes.bytes ?: ByteArray(0),
            fileName = part.fileName,
            contentType = bodyBytes.contentType,
            headers = part.headers
        )
    }
    val multipartBody = builder.build()
    return NetworkBodyBytes(multipartBody.data, multipartBody.contentType)
}

private fun appendPath(url: String, path: String): String {
    val trimmedUrl = url.trimEnd('/')
    val trimmedPath = path.trimStart('/')
    return "$trimmedUrl/$trimmedPath"
}

private fun defaultNetworkMultipartBoundary(): String =
    "KuiklyNetworkBoundary${Random.nextInt(0, Int.MAX_VALUE)}"

private fun encodeUrlComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val builder = StringBuilder()
    bytes.forEach { byte ->
        val intValue = byte.toInt() and 0xff
        val char = intValue.toChar()
        if (char in 'A'..'Z' ||
            char in 'a'..'z' ||
            char in '0'..'9' ||
            char == '-' ||
            char == '_' ||
            char == '.' ||
            char == '~'
        ) {
            builder.append(char)
        } else {
            builder.append('%')
            builder.append(intValue.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return builder.toString()
}
