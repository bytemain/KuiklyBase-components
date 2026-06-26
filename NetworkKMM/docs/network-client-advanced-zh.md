# NetworkClient 进阶能力

这篇文档说明 shared library 或 app shell 在一次普通请求之外常用的能力。

## 接入 auth 和 token refresh

`NetworkAuthConfig` 会注入当前 token，并且对匹配 401 类响应的并发 refresh 做去重。NetworkKMM
只提供 hook，不内置任何业务 token 存储语义。

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

如果 request 已经显式设置了 auth header，`NetworkClient` 不会覆盖它。

## 使用有序 interceptor 包裹请求

需要在每次 engine attempt 前后做稳定顺序的逻辑时，用 interceptor：

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

Interceptors 按列表顺序进入 engine，`proceed` 返回后按反向顺序退出。简单的请求字段改写或响应观察，
仍然可以继续用 `requestMiddlewares` / `responseMiddlewares`。

## 观察上传和下载进度

`NetworkRequest.progress` 支持上传和下载进度回调：

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

内置 `VBTransportNetworkEngine` 会在 materialize request body 时上报上传进度，并在 buffered response
可用时上报下载进度。它目前还不会把 bytes 直接流式写入 platform transport。

## 描述 stream 和 file body

shared 代码能按 chunk 提供数据时，用 `NetworkBody.Stream`。文件由 platform 或业务侧持有时，用
`NetworkBody.FileRef`：

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

由于当前 `VBTransportService` 接收的是 buffered request/response body，内置 engine 会把 `Stream`
和 `FileRef` materialize 成 bytes 作为兼容 fallback。后续 engine 可以实现真正的大文件 streaming，
已经使用这些 body 类型的调用方不需要改 API。

## 检查 engine capability

每个 engine 通过 `NetworkEngine.capabilities` 声明能力：

| Capability | `VBTransportNetworkEngine` |
| --- | --- |
| Request body streaming | `false` |
| Response body streaming | `false` |
| Multipart streaming | `false` |
| Upload progress callback | `true` |
| Download progress callback | `true` |

App 需要判断大文件上传是否可以真 streaming、还是必须走 buffered fallback 时，读取这个 capability。

## 处理稳定错误分类

业务/UI 层用 `NetworkError.kind` 分支，raw code/message 保留给 diagnostics：

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

当前稳定分类包括 `CANCELLED`、`TIMEOUT`、`DNS`、`CONNECT`、`TLS`、`HTTP_STATUS`、`DECODE`、
`AUTH` 和 `UNKNOWN`。
