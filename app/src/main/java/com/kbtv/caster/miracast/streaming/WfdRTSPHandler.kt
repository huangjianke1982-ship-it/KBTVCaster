package com.kbtv.caster.miracast.streaming

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.rtsp.RtspHeaders
import io.netty.handler.codec.rtsp.RtspMethods
import timber.log.Timber

/**
 * WFD RTSP 处理器
 *
 * 处理接收到的 RTSP 请求并返回响应
 * WFD 协议使用标准 RTSP 格式: METHOD rtsp://host RTSP/1.0
 */
class WfdRTSPHandler(
    private val server: WfdRTSPServer
) : io.netty.channel.SimpleChannelInboundHandler<HttpRequest>() {

    companion object {
        private const val TAG = "WfdRTSPHandler"
    }

    override fun channelRead0(ctx: ChannelHandlerContext, request: HttpRequest) {
        try {
            val method = request.method().name()
            val uri = request.uri()
            val headers = extractHeaders(request)
            val cseq = headers["CSeq"] ?: "1"

            Timber.d("收到 RTSP 请求: $method $uri")

            // 解析 RTSP 方法
            val response = when {
                // OPTIONS - 返回支持的 WFD 方法
                method == "OPTIONS" -> {
                    handleOPTIONS(cseq)
                }
                // SETUP - 建立传输会话
                method == "SETUP" -> {
                    val transport = headers["Transport"] ?: ""
                    handleSETUP(cseq, transport)
                }
                // PLAY - 开始流传输
                method == "PLAY" -> {
                    handlePLAY(cseq)
                }
                // TEARDOWN - 结束会话
                method == "TEARDOWN" -> {
                    handleTEARDOWN(cseq)
                }
                // GET_PARAMETER - 查询能力
                method == "GET_PARAMETER" -> {
                    handleGET_PARAMETER(cseq)
                }
                // SET_PARAMETER - 设置参数
                method == "SET_PARAMETER" -> {
                    val body = if (request is FullHttpRequest) {
                        request.content().toString(Charsets.UTF_8)
                    } else null
                    handleSET_PARAMETER(cseq, body)
                }
                else -> {
                    buildErrorResponse(501, "Not Implemented", cseq)
                }
            }

            // 发送响应
            ctx.writeAndFlush(response)
        } catch (e: Exception) {
            Timber.e(e, "处理 RTSP 请求失败")
            ctx.writeAndFlush(buildErrorResponse(500, "Internal Server Error", "1"))
        }
    }

    private fun handleOPTIONS(cseq: String): String {
        return buildString {
            appendLine("RTSP/1.0 200 OK")
            appendLine("CSeq: $cseq")
            appendLine("Public: org.wfa.wfd1.0, OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER")
            appendLine()
        }
    }

    private fun handleSETUP(cseq: String, transport: String): String {
        // 解析 Transport 头，提取客户端端口
        val portMatch = Regex("client_port=(\\d+)").find(transport)
        val clientRtpPort = portMatch?.groupValues?.get(1)?.toIntOrNull() ?: 50000
        val serverRtpPort = 50000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(10000)
        val serverRtcpPort = serverRtpPort + 1

        // 回调
        server.onRTSPSetup(clientRtpPort, serverRtpPort)

        return buildString {
            appendLine("RTSP/1.0 200 OK")
            appendLine("CSeq: $cseq")
            appendLine("Transport: RTP/AVP;unicast;mode=record;server_port=$serverRtpPort-$serverRtcpPort")
            appendLine("Session: 1")
            appendLine()
        }
    }

    private fun handlePLAY(cseq: String): String {
        server.onRTSPPlay()
        return buildString {
            appendLine("RTSP/1.0 200 OK")
            appendLine("CSeq: $cseq")
            appendLine("Range: npt=0-")
            appendLine("Session: 1")
            appendLine()
        }
    }

    private fun handleTEARDOWN(cseq: String): String {
        server.onRTSPTeardown()
        return buildString {
            appendLine("RTSP/1.0 200 OK")
            appendLine("CSeq: $cseq")
            appendLine("Session: 1")
            appendLine()
        }
    }

    private fun handleGET_PARAMETER(cseq: String): String {
        return buildString {
            appendLine("RTSP/1.0 200 OK")
            appendLine("CSeq: $cseq")
            appendLine("Content-Type: text/parameters")
            appendLine("Content-Length: 0")
            appendLine()
        }
    }

    private fun handleSET_PARAMETER(cseq: String, body: String?): String {
        Timber.d("SET_PARAMETER body: $body")
        // 解析 WFD 参数
        body?.split("\r\n")?.forEach { line ->
            if (line.startsWith("wfd_")) {
                Timber.d("WFD 参数: $line")
            }
        }
        return buildString {
            appendLine("RTSP/1.0 200 OK")
            appendLine("CSeq: $cseq")
            appendLine()
        }
    }

    private fun extractHeaders(request: HttpRequest): Map<String, String> {
        return request.headers().names().associateWith { name ->
            request.headers().get(name)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Timber.e(cause, "RTSP 连接异常")
        ctx.close()
    }

    private fun buildErrorResponse(statusCode: Int, message: String, cseq: String): String {
        return buildString {
            appendLine("RTSP/1.0 $statusCode $message")
            appendLine("CSeq: $cseq")
            appendLine()
        }
    }
}
