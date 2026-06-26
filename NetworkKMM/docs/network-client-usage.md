# NetworkClient usage guide

`NetworkClient` is the recommended API for new KMP network call sites. It keeps the existing
`VBTransportService` engine underneath, but gives application code a typed request/response model,
structured body types, cancellation, policies, and middleware hooks.

Use the lower-level `VBTransportService` only when you are maintaining legacy code or implementing a
platform engine.

## Create a request

Use `NetworkRequest` for method, URL/path, query parameters, headers, metadata, policy, progress, and body.

```kotlin
val request = NetworkRequest(
    method = VBTransportMethod.POST,
    url = "https://api.example.com",
    path = "/v1/items",
    body = NetworkBody.Json("""{"name":"Kuikly"}""")
).apply {
    addQuery("source", "shared")
    setHeader("X-Request-Source", "NetworkKMM")
}
```

`url` and `path` can be used together. `NetworkRequest.resolvedUrl()` joins them and appends encoded
query parameters.

## Choose a body type

Pick the body type that matches the payload:

| Use case | Body |
| --- | --- |
| No request body | `NetworkBody.Empty` |
| Binary bytes | `NetworkBody.Bytes(bytes)` |
| Plain text | `NetworkBody.Text(text)` |
| JSON string | `NetworkBody.Json(json)` |
| Form fields | `NetworkBody.Form(fields)` |
| Multipart upload | `NetworkBody.Multipart(parts)` |
| Chunked app-supplied bytes | `NetworkBody.Stream(stream)` |
| Platform file path/reference | `NetworkBody.FileRef(path, ...)` |

Example multipart upload:

```kotlin
val request = NetworkRequest(
    method = VBTransportMethod.POST,
    url = "https://api.example.com/upload",
    body = NetworkBody.Multipart(
        parts = listOf(
            NetworkMultipartPart("name", NetworkBody.Text("Kuikly")),
            NetworkMultipartPart(
                name = "file",
                fileName = "sample.bin",
                body = NetworkBody.Bytes(fileBytes)
            )
        )
    )
)
```

## Execute and read the response

For coroutine call sites, use the suspend API:

```kotlin
val client = NetworkClient()
val response = client.execute(request)

if (response.isSuccess) {
    val text = response.body.text()
    val decoded = response.body.decodeText { raw -> raw.length }
} else {
    val kind = response.error?.kind
    val message = response.error?.message
}
```

For callback call sites, keep the returned `NetworkCall` so the caller can cancel it:

```kotlin
val call = client.execute(request) { response ->
    println(response.statusCode)
}

call.cancel()
```

Cancellation cancels the client coroutine, body stream/file hooks, and the underlying `VBTransportService`
request.

## Set timeout, retry, priority, and dispatcher

Attach a policy to one request when the behavior is specific to that call:

```kotlin
val request = NetworkRequest(
    method = VBTransportMethod.GET,
    url = "https://api.example.com/v1/items",
    policy = NetworkRequestPolicy(
        timeoutMillis = 5_000,
        retry = NetworkRetryPolicy(maxRetries = 2),
        priority = NetworkPriority.HIGH,
        dispatcher = NetworkDispatcher.IO
    )
)
```

Use `NetworkClientConfig.defaultPolicy` or `policySelector` when the app wants one central rule for
groups of requests:

```kotlin
val client = NetworkClient(
    NetworkClientConfig(
        defaultPolicy = NetworkRequestPolicy(timeoutMillis = 10_000),
        policySelector = { request ->
            if (request.metadata["priority"] == "foreground") {
                NetworkRequestPolicy(priority = NetworkPriority.HIGH, timeoutMillis = 5_000)
            } else {
                NetworkRequestPolicy(priority = NetworkPriority.NORMAL)
            }
        }
    )
)
```

## Add common headers

Use request middleware for simple header injection:

```kotlin
val client = NetworkClient(
    NetworkClientConfig(
        requestMiddlewares = listOf(
            NetworkStaticHeadersMiddleware(
                headers = mapOf("X-App" to "Kuikly")
            )
        )
    )
)
```

For token-based auth and refresh, use `NetworkAuthConfig`. See
[Advanced NetworkClient features](./network-client-advanced.md).
