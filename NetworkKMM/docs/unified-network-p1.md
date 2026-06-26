# Unified Network P1 API

This page documents the P1 additions on top of the P0 `NetworkClient` API.

## Ordered Interceptors

Use `NetworkClientConfig.interceptors` when behavior needs to wrap each engine attempt in a predictable order.

```kotlin
val client = NetworkClient(
    NetworkClientConfig(
        interceptors = listOf(
            object : NetworkInterceptor {
                override suspend fun intercept(chain: NetworkInterceptorChain): NetworkResponse {
                    val request = chain.request.apply {
                        setHeader("X-Trace-Source", "shared")
                    }
                    val response = chain.proceed(request)
                    return response
                }
            }
        )
    )
)
```

Interceptors run in list order before the engine and unwind in reverse order after `proceed`. Existing
`requestMiddlewares` and `responseMiddlewares` remain supported for simpler mutation/observe use cases.

## Progress

`NetworkRequest.progress` accepts upload and download progress callbacks.

```kotlin
val request = NetworkRequest(
    method = VBTransportMethod.POST,
    url = "https://api.example.com/upload",
    body = NetworkBody.Stream(
        NetworkByteStream.fromChunks(contentLength = fileSize) { sink ->
            // platform or app code writes chunks here
        }
    ),
    progress = NetworkProgressCallbacks(
        uploadProgress = { progress ->
            val sent = progress.bytesTransferred
            val total = progress.bytesTotal
        },
        downloadProgress = { progress ->
            val read = progress.bytesTransferred
            val total = progress.bytesTotal
        }
    )
)
```

The default `VBTransportNetworkEngine` reports progress while materializing request bodies and when buffered
responses are available. It does not yet stream bytes directly into the platform transport.

## Streaming Model

`NetworkByteStream.fromChunks` and `NetworkBody.FileRef(openStreamBlock = ...)` let callers describe large request
bodies without requiring a single `ByteArray` in the public model. `NetworkEngine.capabilities` advertises whether an
engine can stream request bodies, response bodies, multipart bodies, and progress natively.

Current built-in engine capabilities:

| Capability | `VBTransportNetworkEngine` |
| --- | --- |
| Request body streaming | `false` |
| Response body streaming | `false` |
| Multipart streaming | `false` |
| Upload progress callback | `true` |
| Download progress callback | `true` |

Because the existing `VBTransportService` bridge accepts buffered request/response bodies, the built-in engine still
materializes bodies as a compatibility fallback. A future native engine can implement true large-file streaming without
changing callers that already use `NetworkBody.Stream` or `FileRef(openStreamBlock = ...)`.

## Error Taxonomy

`NetworkError.kind` is classified into stable buckets: `CANCELLED`, `TIMEOUT`, `DNS`, `CONNECT`, `TLS`,
`HTTP_STATUS`, `DECODE`, `AUTH`, and `UNKNOWN`. `rawCode`, `statusCode`, `message`, and `cause` are preserved for
diagnostics, while UI/business layers can switch on `kind`.
