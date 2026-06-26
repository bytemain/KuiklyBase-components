# Advanced NetworkClient features

This page covers the `NetworkClient` features used by shared libraries and app shells that need more
than a one-off request.

## Add auth and token refresh

`NetworkAuthConfig` injects the current token and deduplicates concurrent refresh calls for matching
401-style responses. The core library only provides hooks; app-specific token storage stays outside
NetworkKMM.

```kotlin
class TokenProvider : NetworkTokenProvider {
    override suspend fun currentToken(request: NetworkRequest): String? = loadToken()

    override suspend fun refreshToken(
        request: NetworkRequest,
        response: NetworkResponse
    ): String? = refreshTokenOnce()
}

val client = NetworkClient(
    NetworkClientConfig(
        auth = NetworkAuthConfig(
            tokenProvider = TokenProvider(),
            headerName = "Authorization",
            refreshStatusCodes = setOf(401),
            formatToken = { token -> "Bearer $token" }
        )
    )
)
```

If a request already sets the auth header, `NetworkClient` does not overwrite it.

## Wrap calls with ordered interceptors

Use interceptors when behavior needs to run around each engine attempt in a predictable order:

```kotlin
val client = NetworkClient(
    NetworkClientConfig(
        interceptors = listOf(
            object : NetworkInterceptor {
                override suspend fun intercept(chain: NetworkInterceptorChain): NetworkResponse {
                    val startedAt = currentTimeMillis()
                    val response = chain.proceed(
                        chain.request.apply { setHeader("X-Trace-Source", "shared") }
                    )
                    recordNetworkTiming(chain.request, response, currentTimeMillis() - startedAt)
                    return response
                }
            }
        )
    )
)
```

Interceptors run in list order before the engine and unwind in reverse order after `proceed`.
`requestMiddlewares` and `responseMiddlewares` remain useful for simple request mutation or response
observation.

## Observe upload and download progress

`NetworkRequest.progress` accepts upload and download callbacks:

```kotlin
val request = NetworkRequest(
    method = VBTransportMethod.POST,
    url = "https://api.example.com/upload",
    body = NetworkBody.Stream(
        NetworkByteStream.fromChunks(contentLength = fileSize) { sink ->
            sink.write(firstChunk)
            sink.write(secondChunk)
        }
    ),
    progress = NetworkProgressCallbacks(
        uploadProgress = { progress ->
            println("${progress.bytesTransferred}/${progress.bytesTotal}")
        },
        downloadProgress = { progress ->
            println("downloaded ${progress.bytesTransferred}")
        }
    )
)
```

The built-in `VBTransportNetworkEngine` reports upload progress while materializing request bodies and
download progress when buffered responses are available. It does not yet stream bytes directly into the
platform transport.

## Describe stream and file bodies

Use `NetworkBody.Stream` when shared code can provide chunks. Use `NetworkBody.FileRef` when platform or
app code owns the file path and can provide bytes or a stream:

```kotlin
val body = NetworkBody.FileRef(
    path = filePath,
    contentType = "application/octet-stream",
    contentLength = fileSize,
    openStreamBlock = { path ->
        openPlatformFileStream(path)
    },
    cancelBlock = {
        cancelPlatformRead()
    }
)
```

Because `VBTransportService` currently accepts buffered request/response bodies, the built-in engine
materializes `Stream` and `FileRef` as a compatibility fallback. Future engines can implement true
large-file streaming without changing call sites that already use these body types.

## Check engine capabilities

Each engine declares what it can do through `NetworkEngine.capabilities`:

| Capability | `VBTransportNetworkEngine` |
| --- | --- |
| Request body streaming | `false` |
| Response body streaming | `false` |
| Multipart streaming | `false` |
| Upload progress callback | `true` |
| Download progress callback | `true` |

Use this when an app needs to decide whether a large upload can be sent as a true stream or must use the
buffered fallback.

## Handle stable error kinds

Use `NetworkError.kind` for business/UI branching and keep raw codes/messages for diagnostics:

```kotlin
when (response.error?.kind) {
    NetworkErrorKind.CANCELLED -> return
    NetworkErrorKind.TIMEOUT -> showRetry()
    NetworkErrorKind.DNS,
    NetworkErrorKind.CONNECT,
    NetworkErrorKind.TLS -> showNetworkUnavailable()
    NetworkErrorKind.AUTH -> requestLogin()
    NetworkErrorKind.HTTP_STATUS -> showServerError(response.statusCode)
    NetworkErrorKind.DECODE -> showDataError()
    NetworkErrorKind.UNKNOWN,
    null -> showUnknownError()
}
```

Current stable buckets are `CANCELLED`, `TIMEOUT`, `DNS`, `CONNECT`, `TLS`, `HTTP_STATUS`, `DECODE`,
`AUTH`, and `UNKNOWN`.
