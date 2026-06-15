package com.kbtv.caster.airplay.server

import com.kbtv.caster.airplay.AirPlayManager
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.stream.ChunkedWriteHandler
import timber.log.Timber

/**
 * AirPlay HTTP 服务器
 *
 * 基于 Netty 实现的 HTTP/1.1 服务器，监听 iOS 设备发来的 AirPlay 指令。
 * 注意：尽管类名叫 RTSPServer（保留以兼容老代码），AirPlay 协议在此端口走的是 HTTP/1.1。
 *
 * 处理端点：
 *  - GET  /server-info, /info, /playback-info  → 设备信息/播放状态（XML plist）
 *  - POST /play        → 播放视频 URL（text/parameters body）
 *  - POST /stop        → 停止播放
 *  - POST /rate?value=N → 0=暂停, 1=播放
 *  - GET/POST /scrub   → 查询/跳转进度
 *  - PUT  /photo       → 接收图片（MVP 仅日志）
 *  - POST /reverse     → iOS 反向通道（忽略）
 *  - OPTIONS *         → CORS 预检
 *  - GET  /stream.xml  → 镜像能力（最小化返回）
 */
class AirPlayRTSPServer(
    private val port: Int = 7000,
    private val manager: AirPlayManager
) {
    private var serverChannel: Channel? = null
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null

    /**
     * 启动 HTTP 监听。同步返回，Netty 的 bind().sync() 通常 <50ms。
     * @return true 表示端口已成功监听
     */
    fun start(): Boolean {
        return try {
            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup()

            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline()
                            .addLast("decoder", HttpRequestDecoder())
                            .addLast("encoder", HttpResponseEncoder())
                            // 支持 PUT /photo 大体分块写回（虽然 MVP 只返回 200，保留以防未来扩展）
                            .addLast("chunkedWriter", ChunkedWriteHandler())
                            .addLast("handler", AirPlayHTTPHandler(manager))
                    }
                })

            serverChannel = bootstrap.bind(port).sync().channel()
            Timber.i("AirPlay HTTP 服务器已启动，端口: $port")
            true
        } catch (e: Exception) {
            Timber.e(e, "AirPlay HTTP 服务器启动失败")
            false
        }
    }

    /**
     * 停止 HTTP 监听并释放 Netty 线程组。可重入。
     */
    fun stop() {
        try {
            serverChannel?.close()
            bossGroup?.shutdownGracefully()
            workerGroup?.shutdownGracefully()
        } catch (e: Exception) {
            Timber.w(e, "AirPlay HTTP 服务器停止异常")
        }
        serverChannel = null
        bossGroup = null
        workerGroup = null
        Timber.d("AirPlay HTTP 服务器已停止")
    }
}

/**
 * AirPlay HTTP 请求处理器。
 *
 * 必须接收 FullHttpRequest — pipeline 的 HttpRequestDecoder 会产出请求 + 后续 HttpContent，
 * SimpleChannelInboundHandler<FullHttpRequest> 自动等待 LastHttpContent 聚合成完整请求，
 * 这样才能读取 /play 的 body 与 /photo 的二进制数据。
 *
 * 关键修复点：响应必须用 DefaultFullHttpResponse，绝不能直接 writeAndFlush(String) —
 * 后者缺 HTTP 编码器匹配会导致 ClassCastException 并打爆 Netty 线程。
 */
class AirPlayHTTPHandler(
    private val manager: AirPlayManager
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        val method = request.method()
        val uri = request.uri()
        val path = uri.substringBefore('?')
        val query = uri.substringAfter('?', "")
        val body = request.content().toString(Charsets.UTF_8)

        Timber.d("AirPlay 收到: ${method.name()} $uri")

        val response = try {
            dispatch(method, path, query, request, body)
        } catch (e: Exception) {
            Timber.e(e, "AirPlay 处理请求异常: ${method.name()} $path")
            buildResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, "error\n")
        }

        ctx.writeAndFlush(response)
    }

    private fun dispatch(
        method: HttpMethod,
        path: String,
        query: String,
        request: FullHttpRequest,
        body: String
    ): DefaultFullHttpResponse {
        // 跨域预检（iOS AirPlay 在投屏前会发 OPTIONS）
        if (method == HttpMethod.OPTIONS) {
            return buildResponse(HttpResponseStatus.OK, "").apply {
                headers().set(HttpHeaderNames.ALLOW, "OPTIONS, GET, POST, PUT, DELETE")
            }
        }

        return when {
            // 设备发现/能力查询
            path == "/server-info" || path == "/info" -> handleServerInfo()
            path == "/stream.xml" -> handleStreamXml()

            // 播放状态
            path == "/playback-info" -> handlePlaybackInfo()

            // 视频控制
            method == HttpMethod.POST && path == "/play" -> handlePlay(request, body)
            method == HttpMethod.POST && path == "/stop" -> handleStop()
            method == HttpMethod.POST && path == "/rate" -> handleRate(query)
            method == HttpMethod.POST && path == "/scrub" ||
            method == HttpMethod.GET && path == "/scrub" -> handleScrub(method, query)
            method == HttpMethod.POST && path == "/reverse" -> buildResponse(HttpResponseStatus.OK, "")

            // 图片投送（MVP：仅日志）
            method == HttpMethod.PUT && path == "/photo" -> handlePhoto(request)

            else -> {
                Timber.w("AirPlay 未处理的请求: ${method.name()} $path")
                buildResponse(HttpResponseStatus.NOT_FOUND, "not found\n")
            }
        }
    }

    /**
     * GET /server-info — 返回设备信息 XML plist。
     * iOS 用此响应判断目标是否为 AirPlay 接收端。
     */
    private fun handleServerInfo(): DefaultFullHttpResponse {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>deviceid</key><string>AA:BB:CC:DD:EE:FF</string>
  <key>features</key><integer>1518338815</integer>
  <key>model</key><string>AppleTV3,2</string>
  <key>protovers</key><string>1.1</string>
  <key>srcvers</key><string>220.68</string>
</dict>
</plist>
""".trimIndent()
        return buildResponse(HttpResponseStatus.OK, xml, "text/x-apple-plist+xml")
    }

    /**
     * GET /stream.xml — 镜像能力查询。MVP 不支持镜像，返回最小声明。
     */
    private fun handleStreamXml(): DefaultFullHttpResponse {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<StreamConfig><version>1</version><deviceID>AA:BB:CC:DD:EE:FF</deviceID></StreamConfig>
""".trimIndent()
        return buildResponse(HttpResponseStatus.OK, xml, "text/xml")
    }

    /**
     * GET /playback-info — 当前播放状态。
     * iOS 用此判断是否可控制（rate/readyToPlay 等）。
     */
    private fun handlePlaybackInfo(): DefaultFullHttpResponse {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>rate</key><real>0.0</real>
  <key>readyToPlay</key><false/>
  <key>playbackBufferEmpty</key><true/>
  <key>playbackBufferFull</key><false/>
  <key>playbackLikelyToKeepUp</key><false/>
</dict></plist>
""".trimIndent()
        return buildResponse(HttpResponseStatus.OK, xml, "text/x-apple-plist+xml")
    }

    /**
     * POST /play — iOS 把视频 URL 通过 text/parameters body 发来。
     * 示例 body:
     *   Content-Location: https://example.com/video.mp4
     *   Start-Position: 0
     * 兼容两种键：Content-Location（标准）和 url（部分客户端）
     */
    private fun handlePlay(request: FullHttpRequest, body: String): DefaultFullHttpResponse {
        // 优先从 header 取（部分 iOS 版本把 URL 放 header）
        val headerUrl = request.headers().get("Content-Location")
            ?: request.headers().get("X-Apple-Content-Location")

        val bodyUrl = parseTextParameters(body)["Content-Location"]
            ?: parseTextParameters(body)["url"]

        val url = headerUrl ?: bodyUrl
        if (url.isNullOrBlank()) {
            Timber.w("AirPlay /play 缺少 URL — body: $body")
            return buildResponse(HttpResponseStatus.BAD_REQUEST, "missing url\n")
        }

        Timber.i("AirPlay 播放: $url")
        manager.onPlayUrl?.invoke(url)
        return buildResponse(HttpResponseStatus.OK, "")
    }

    /**
     * POST /stop — 停止当前播放。
     */
    private fun handleStop(): DefaultFullHttpResponse {
        Timber.i("AirPlay 停止播放")
        manager.onStopPlayback?.invoke()
        return buildResponse(HttpResponseStatus.OK, "")
    }

    /**
     * POST /rate?value=N — iOS 播放/暂停切换。
     * value=1.0 (或 >0) = 播放，value=0 = 暂停。
     */
    private fun handleRate(query: String): DefaultFullHttpResponse {
        val params = parseQueryString(query)
        val value = params["value"]?.toFloatOrNull() ?: 1.0f
        Timber.i("AirPlay rate: value=$value (${if (value > 0f) "播放" else "暂停"})")
        manager.onPlayPause?.invoke(value > 0f)
        return buildResponse(HttpResponseStatus.OK, "")
    }

    /**
     * POST /scrub?position=N — 跳转到指定位置（秒）。
     * GET /scrub — 返回当前播放位置（text/parameters）。
     */
    private fun handleScrub(method: HttpMethod, query: String): DefaultFullHttpResponse {
        if (method == HttpMethod.POST) {
            val positionSec = parseQueryString(query)["position"]?.toFloatOrNull() ?: 0f
            val positionMs = (positionSec * 1000f).toLong()
            Timber.i("AirPlay scrub 跳转: ${positionSec}s ($positionMs ms)")
            manager.onSeek?.invoke(positionMs)
            return buildResponse(HttpResponseStatus.OK, "")
        }
        // GET — 简单返回 0，MVP 不向 iOS 反馈真实位置（避免与 ExoPlayer 同步复杂性）
        val body = "duration: 0.0\nposition: 0.0\n"
        return buildResponse(HttpResponseStatus.OK, body)
    }

    /**
     * PUT /photo — 接收 JPEG 二进制。MVP 仅记录长度，后续可对接 ImageView 显示。
     */
    private fun handlePhoto(request: FullHttpRequest): DefaultFullHttpResponse {
        val length = HttpUtil.getContentLength(request, 0L)
        Timber.i("AirPlay 收到图片，大小: $length bytes")
        // TODO: 将 request.content() 的 NIO ByteBuffer 转成 Bitmap 推到 UI
        return buildResponse(HttpResponseStatus.OK, "")
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Timber.e(cause, "AirPlay HTTP 连接异常")
        ctx.close()
    }

    // ===== 响应构建与解析工具 =====

    /**
     * 构建 HTTP 响应。统一加上 Server: AirTunes/220.68 头，
     * iOS 客户端用此字段识别 AirPlay 接收端。
     */
    private fun buildResponse(
        status: HttpResponseStatus = HttpResponseStatus.OK,
        body: String = "",
        contentType: String = "text/parameters"
    ): DefaultFullHttpResponse {
        val buf = Unpooled.copiedBuffer(body, Charsets.UTF_8)
        val resp = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf)
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes())
        resp.headers().set(HttpHeaderNames.SERVER, "AirTunes/220.68")
        resp.headers().set("CSeq", "1")
        return resp
    }

    /**
     * 解析 text/parameters 格式（每行 "Key: Value"）。
     */
    private fun parseTextParameters(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        return body.lineSequence()
            .filter { it.contains(':') }
            .associate {
                val idx = it.indexOf(':')
                it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            }
    }

    /**
     * 解析 URL query string（a=1&b=2）。
     */
    private fun parseQueryString(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&')
            .filter { it.contains('=') }
            .associate {
                val idx = it.indexOf('=')
                it.substring(0, idx) to it.substring(idx + 1)
            }
    }
}
