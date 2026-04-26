package com.spotify.music.hooks.spotify.features.network.clienttoken

import com.spotify.music.core.utils.Logger
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class RequestListener(port: Int) : NanoHTTPD(port) {
    init {
        try {
            start()
        } catch (ex: IOException) {
            throw RuntimeException("Failed to start request listener on port $port", ex)
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Logger.info("[ClientToken] Incoming ${session.method} $uri")

        if (uri != Constants.CLIENT_TOKEN_API_PATH) {
            Logger.warn("[ClientToken] Ignored URI: $uri")
            return INTERNAL_ERROR_RESPONSE
        }

        val responseBytes = runCatching {
            ClientTokenService.serveClientTokenRequest(getInputStream(session), session.headers)
        }.onFailure { err ->
            Logger.error("[ClientToken] Failed to serve request for URI: $uri", err)
        }.getOrNull()

        return if (responseBytes != null) {
            Logger.info("[ClientToken] Served URI: $uri bytes=${responseBytes.size}")
            newResponse(Response.Status.OK, responseBytes)
        } else {
            Logger.warn("[ClientToken] Service returned no response for URI: $uri")
            INTERNAL_ERROR_RESPONSE
        }
    }

    private fun newLimitedInputStream(inputStream: InputStream, contentLength: Long): InputStream {
        return object : FilterInputStream(inputStream) {
            private var remaining = contentLength

            override fun read(): Int {
                if (remaining <= 0) return -1
                val result = super.read()
                if (result != -1) remaining--
                return result
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val limitedLength = minOf(len.toLong(), remaining).toInt()
                val result = super.read(b, off, limitedLength)
                if (result != -1) remaining -= result.toLong()
                return result
            }
        }
    }

    private fun getInputStream(session: IHTTPSession): InputStream {
        val requestContentLength = session.headers["content-length"]?.toLongOrNull()
        return if (requestContentLength != null && requestContentLength >= 0L) {
            newLimitedInputStream(session.inputStream, requestContentLength)
        } else {
            session.inputStream
        }
    }

    private val INTERNAL_ERROR_RESPONSE: Response = newResponse(INTERNAL_ERROR)

    private fun newResponse(status: Response.IStatus, responseBytes: ByteArray? = null): Response {
        if (responseBytes == null) {
            return newFixedLengthResponse(status, "application/x-protobuf", ByteArrayInputStream(ByteArray(0)), 0)
        }

        return newFixedLengthResponse(
            status,
            "application/x-protobuf",
            ByteArrayInputStream(responseBytes),
            responseBytes.size.toLong(),
        )
    }
}