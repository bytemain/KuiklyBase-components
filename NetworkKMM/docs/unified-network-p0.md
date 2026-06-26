# Unified Network P0 API

`NetworkClient` is the P0 compatibility layer for the bytemain NetworkKMM fork. It keeps the existing
`VBTransportService` APIs available, while adding a stable KMP-facing request/response model for new callers.

## Request Model

```kotlin
val request = NetworkRequest(
    method = VBTransportMethod.POST,
    url = "https://api.example.com",
    path = "/v1/items",
    body = NetworkBody.Json("""{"name":"Kuikly"}"""),
    policy = NetworkRequestPolicy(
        timeoutMillis = 5_000,
        retry = NetworkRetryPolicy(maxRetries = 2),
        priority = NetworkPriority.HIGH,
        dispatcher = NetworkDispatcher.IO
    )
).apply {
    addQuery("source", "shared")
    setHeader("X-Request-Source", "NetworkKMM")
}
```

The model covers method, absolute URL, optional path/query fields, headers, metadata, policy, and these body
variants:

- `NetworkBody.Bytes`
- `NetworkBody.Text`
- `NetworkBody.Json`
- `NetworkBody.Form`
- `NetworkBody.Multipart`
- `NetworkBody.Stream`
- `NetworkBody.FileRef`

`baseUrl` and environment rewrite are intentionally not part of this P0 pass. They are tracked separately as a
deferred endpoint resolver.

## Response Model

```kotlin
val client = NetworkClient()
val response = client.execute(request)

val status = response.statusCode
val headers = response.headers
val text = response.body.text()
val decoded = response.body.decodeText { raw -> raw.length }
val error = response.error
```

The new response model exposes status, headers, body bytes, a replayable byte stream for buffered responses,
typed decode hooks, stable error kind, raw VB transport response, and timing.

## Cancellation

```kotlin
val call = client.execute(request) { response ->
    // handle response
}

call.cancel()
```

Cancellation cancels the client coroutine, any request body stream/file reference hook, and the underlying
`VBTransportService` request.

## Auth And Headers

```kotlin
class TokenProvider : NetworkTokenProvider {
    override suspend fun currentToken(request: NetworkRequest): String? = loadToken()

    override suspend fun refreshToken(request: NetworkRequest, response: NetworkResponse): String? {
        return refreshTokenOnce()
    }
}

val client = NetworkClient(
    NetworkClientConfig(
        requestMiddlewares = listOf(
            NetworkStaticHeadersMiddleware(mapOf("X-App" to "Slock"))
        ),
        auth = NetworkAuthConfig(TokenProvider())
    )
)
```

The core library only provides hooks. It does not hard-code Slock token semantics. `NetworkAuthConfig` injects the
current token and deduplicates concurrent refresh calls for matching 401 responses.

## Retry And Policy

`NetworkRequestPolicy` supports timeout, retry/backoff, priority, dispatcher, and metadata-driven selection through
`NetworkClientConfig.policySelector`.
