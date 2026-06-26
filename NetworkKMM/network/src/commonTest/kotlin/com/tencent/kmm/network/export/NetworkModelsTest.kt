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

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NetworkModelsTest {
    @Test
    fun resolvedUrlAppendsPathAndEscapedQuery() {
        val request = NetworkRequest(
            method = VBTransportMethod.GET,
            url = "https://example.com/api",
            path = "/v1/search"
        ).apply {
            addQuery("q", "hello world")
            addQuery("tag", "a/b")
        }

        assertEquals("https://example.com/api/v1/search?q=hello%20world&tag=a%2Fb", request.resolvedUrl())
    }

    @Test
    fun retryPolicyMatchesStatusAndErrorKind() {
        val request = NetworkRequest()
        val retry = NetworkRetryPolicy(maxRetries = 1)
        val statusResponse = NetworkResponse(
            request = request,
            statusCode = 503,
            headers = emptyMap(),
            body = NetworkResponseBody(),
            error = NetworkError(NetworkErrorKind.HTTP_STATUS, "Service Unavailable", 503)
        )
        val timeoutResponse = NetworkResponse(
            request = request,
            statusCode = null,
            headers = emptyMap(),
            body = NetworkResponseBody(),
            error = NetworkError(NetworkErrorKind.TIMEOUT, "timeout")
        )

        assertTrue(retry.shouldRetry(statusResponse))
        assertTrue(retry.shouldRetry(timeoutResponse))
    }

    @Test
    fun streamAndFileRefBodiesAreMarkedNonRepeatableWithoutReader() {
        val streamBody = NetworkBody.Stream(NetworkByteStream(readAllBlock = { byteArrayOf(1, 2, 3) }))
        val fileRefBody = NetworkBody.FileRef(path = "/tmp/file.bin")

        assertFalse(streamBody.repeatable)
        assertFalse(fileRefBody.repeatable)
    }

    @Test
    fun multipartBodySerializesThroughSharedTransportBuilder() = runBlocking {
        val body = NetworkBody.Multipart(
            parts = listOf(
                NetworkMultipartPart("meta", NetworkBody.Text("hello")),
                NetworkMultipartPart(
                    name = "file",
                    fileName = "sample.txt",
                    headers = mapOf("X-Part" to "ok"),
                    body = NetworkBody.Text("payload", contentType = "text/plain")
                )
            ),
            boundary = "BoundaryForTest"
        )

        val bodyBytes = body.toBytes()
        val raw = assertNotNull(bodyBytes.bytes).decodeToString()

        assertEquals("multipart/form-data; boundary=BoundaryForTest", bodyBytes.contentType)
        assertTrue(raw.contains("--BoundaryForTest\r\n"))
        assertTrue(raw.contains("Content-Disposition: form-data; name=\"meta\""))
        assertTrue(raw.contains("Content-Type: text/plain; charset=utf-8"))
        assertTrue(raw.contains("Content-Disposition: form-data; name=\"file\"; filename=\"sample.txt\""))
        assertTrue(raw.contains("X-Part: ok"))
        assertTrue(raw.contains("payload"))
    }

    @Test
    fun multipartBodyPropagatesPartBodyErrors() = runBlocking {
        val body = NetworkBody.Multipart(
            parts = listOf(
                NetworkMultipartPart("file", NetworkBody.FileRef(path = "/tmp/missing-reader.bin"))
            ),
            boundary = "BoundaryForTest"
        )

        val bodyBytes = body.toBytes()

        assertNull(bodyBytes.bytes)
        assertEquals(NetworkErrorKind.UNKNOWN, bodyBytes.error?.kind)
    }
}
