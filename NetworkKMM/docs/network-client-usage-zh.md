# NetworkClient 使用指南

新的 KMP 网络调用建议使用 `NetworkClient`。它底层仍可复用现有 `VBTransportService` engine，
但业务代码面对的是 typed request/response model、结构化 body、取消、策略和 middleware hook。

只有维护历史代码或实现底层 platform engine 时，才建议直接使用 `VBTransportService`。

## 创建请求

`NetworkRequest` 用来描述 method、URL/path、query、headers、metadata、policy、progress 和 body。

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

`url` 和 `path` 可以组合使用，`NetworkRequest.resolvedUrl()` 会拼接 path，并追加编码后的 query。

## 选择 body 类型

按 payload 类型选择 body：

| 场景 | Body |
| --- | --- |
| 无请求体 | `NetworkBody.Empty` |
| 二进制 bytes | `NetworkBody.Bytes(bytes)` |
| 普通文本 | `NetworkBody.Text(text)` |
| JSON 字符串 | `NetworkBody.Json(json)` |
| 表单字段 | `NetworkBody.Form(fields)` |
| multipart 上传 | `NetworkBody.Multipart(parts)` |
| 调用方按 chunk 提供数据 | `NetworkBody.Stream(stream)` |
| platform 文件路径/引用 | `NetworkBody.FileRef(path, ...)` |

Multipart 上传示例：

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

## 发起请求并读取响应

协程调用点使用 suspend API：

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

Callback 调用点保留返回的 `NetworkCall`，用于后续取消：

```kotlin
val call = client.execute(request) { response ->
    println(response.statusCode)
}

call.cancel()
```

取消会同时取消 client coroutine、body stream/file hook，以及底层 `VBTransportService` 请求。

## 设置 timeout、retry、priority 和 dispatcher

某个请求有特殊策略时，直接把 policy 放在 request 上：

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

如果 App 想集中管理一类请求的策略，使用 `NetworkClientConfig.defaultPolicy` 或 `policySelector`：

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

## 添加公共 header

简单 header 注入使用 request middleware：

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

Token 鉴权和刷新请使用 `NetworkAuthConfig`，见 [NetworkClient 进阶能力](./network-client-advanced-zh.md)。
