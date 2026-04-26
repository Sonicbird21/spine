package com.spotify.music.hooks.spotify.features.network.clienttoken

import com.spotify.music.core.utils.Logger
import com.spotify.music.hooks.spotify.features.network.clienttoken.Constants.CLIENT_TOKEN_API_URL
import com.spotify.music.hooks.spotify.features.network.clienttoken.Constants.getClientVersion
import com.spotify.music.hooks.spotify.features.network.clienttoken.Constants.getHardwareMachine
import com.spotify.music.hooks.spotify.features.network.clienttoken.Constants.getSystemVersion
import com.spotify.music.hooks.spotify.features.network.clienttoken.proto.ClienttokenHttp.ClientDataRequest
import com.spotify.music.hooks.spotify.features.network.clienttoken.proto.ClienttokenHttp.ClientTokenRequest
import com.spotify.music.hooks.spotify.features.network.clienttoken.proto.ClienttokenHttp.ClientTokenRequestType
import com.spotify.music.hooks.spotify.features.network.clienttoken.proto.ClienttokenHttp.ConnectivitySdkData
import com.spotify.music.hooks.spotify.features.network.clienttoken.proto.ClienttokenHttp.NativeIOSData
import com.spotify.music.hooks.spotify.features.network.clienttoken.proto.ClienttokenHttp.PlatformSpecificData
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object ClientTokenService {
    private const val IOS_CLIENT_ID = "58bd3c95768941ea9eb4350aaa033eb3"
    private const val IOS_USER_INTERFACE_IDIOM_PHONE = 0
    private const val IOS_TARGET_IPHONE_SIMULATOR = false
    private const val IOS_HW_MACHINE = "iPhone14,7"
    private const val IOS_SYSTEM_VERSION = "17.5.1"
    private const val IOS_SIMULATOR_MODEL_IDENTIFIER = "iPhone14,7"

    private val iosUserAgent: String by lazy { buildIosUserAgent() }

    fun getClientTokenResponse(
        request: ClientTokenRequest,
        rawRequestBytes: ByteArray,
        requestHeaders: Map<String, String>,
    ): ByteArray? {
        val requestBytes = if (request.requestType == ClientTokenRequestType.REQUEST_CLIENT_DATA_REQUEST) {
            val spoofed = spoofClientDataPayload(request, rawRequestBytes)
            Logger.info(
                "Forwarding spoofed client-data payload (original=${rawRequestBytes.size} bytes, spoofed=${spoofed.size} bytes)"
            )
            spoofed
        } else {
            Logger.info("Forwarding client-token payload unchanged (type=${request.requestType}, bytes=${rawRequestBytes.size})")
            rawRequestBytes
        }

        return try {
            requestClientToken(requestBytes, requestHeaders)
        } catch (ex: IOException) {
            Logger.error("Failed to handle request", ex)
            null
        }
    }

    fun serveClientTokenRequest(inputStream: InputStream, requestHeaders: Map<String, String>): ByteArray? {
        val rawRequestBytes = try {
            inputStream.readBytes()
        } catch (ex: IOException) {
            Logger.error("Failed to read request payload", ex)
            return null
        }

        val request = try {
            ClientTokenRequest.parseFrom(rawRequestBytes)
        } catch (ex: IOException) {
            Logger.error("Failed to parse request from input stream", ex)
            return null
        }

        Logger.info("Request of type: ${request.requestType}")

        val responseBytes = getClientTokenResponse(request, rawRequestBytes, requestHeaders)
        if (responseBytes != null) {
            Logger.info("Received upstream client-token response bytes=${responseBytes.size}")
        }

        return responseBytes
    }

    private fun requestClientToken(requestBytes: ByteArray, requestHeaders: Map<String, String>): ByteArray {
        val urlConnection = (URL(CLIENT_TOKEN_API_URL).openConnection() as HttpURLConnection)

        urlConnection.requestMethod = "POST"
        urlConnection.doOutput = true

        val inboundCacheControl = requestHeaders["cache-control"]
        val inboundContentType = requestHeaders["content-type"]
        val inboundAccept = requestHeaders["accept"]
        val inboundAcceptEncoding = requestHeaders["accept-encoding"]

        urlConnection.setRequestProperty("Cache-Control", inboundCacheControl ?: "no-cache, no-store, max-age=0")
        urlConnection.setRequestProperty("Content-Type", inboundContentType ?: "application/x-protobuf")
        urlConnection.setRequestProperty("Accept", inboundAccept ?: "application/x-protobuf")
        urlConnection.setRequestProperty("Accept-Encoding", inboundAcceptEncoding ?: "gzip")
        urlConnection.setRequestProperty("User-Agent", iosUserAgent)

        urlConnection.setFixedLengthStreamingMode(requestBytes.size)
        urlConnection.connectTimeout = 15_000
        urlConnection.readTimeout = 15_000

        urlConnection.outputStream.use { outputStream ->
            outputStream.write(requestBytes)
            outputStream.flush()
        }

        val responseStream = runCatching { urlConnection.inputStream }
            .getOrElse { urlConnection.errorStream ?: throw it }
        val decodedResponseStream = decodeResponseStream(urlConnection, responseStream)

        decodedResponseStream.use { inputStream ->
            return inputStream.readBytes()
        }
    }

    private fun spoofClientDataPayload(request: ClientTokenRequest, rawRequestBytes: ByteArray): ByteArray {
        val clientData = request.clientData
        if (clientData == null) {
            Logger.warn("[ClientToken] Missing client_data, forwarding original payload")
            return rawRequestBytes
        }

        val deviceId = clientData.connectivitySdkData?.deviceId.orEmpty()
        val rebuilt = ClientTokenRequest(
            requestType = ClientTokenRequestType.REQUEST_CLIENT_DATA_REQUEST,
            clientData = ClientDataRequest(
                clientVersion = clientData.clientVersion.ifBlank { getClientVersion() },
                clientId = IOS_CLIENT_ID,
                currentToken = clientData.currentToken,
                connectivitySdkData = ConnectivitySdkData(
                    platformSpecificData = PlatformSpecificData(
                        ios = NativeIOSData(
                            userInterfaceIdiom = IOS_USER_INTERFACE_IDIOM_PHONE,
                            targetIphoneSimulator = IOS_TARGET_IPHONE_SIMULATOR,
                            hwMachine = IOS_HW_MACHINE,
                            systemVersion = IOS_SYSTEM_VERSION,
                            simulatorModelIdentifier = IOS_SIMULATOR_MODEL_IDENTIFIER,
                        )
                    ),
                    deviceId = deviceId,
                ),
            ),
        )

        Logger.info(
            "[ClientToken] Rebuilt client_data as NativeIOSData(user_interface_idiom=$IOS_USER_INTERFACE_IDIOM_PHONE, " +
                "target_iphone_simulator=$IOS_TARGET_IPHONE_SIMULATOR, hw_machine=$IOS_HW_MACHINE, " +
                "system_version=$IOS_SYSTEM_VERSION, simulator_model_identifier=$IOS_SIMULATOR_MODEL_IDENTIFIER)"
        )
        return rebuilt.toByteArray()
    }

    private fun decodeResponseStream(urlConnection: HttpURLConnection, responseStream: InputStream): InputStream {
        val contentEncoding = urlConnection.getHeaderField("Content-Encoding")
            ?.trim()
            ?.lowercase()
            .orEmpty()

        return if (contentEncoding.contains("gzip")) {
            GZIPInputStream(responseStream)
        } else {
            responseStream
        }
    }

    private fun buildIosUserAgent(): String {
        val clientVersion = getClientVersion()
        val version = extractUserAgentVersion(clientVersion)
        return "Spotify/$version iOS/${getSystemVersion()} (iPhone14,7)"
    }

    private fun extractUserAgentVersion(clientVersion: String): String {
        val hyphenIndex = clientVersion.indexOf('-')
        val commitHashIndex = clientVersion.lastIndexOf('.')
        if (hyphenIndex < 0 || commitHashIndex <= hyphenIndex) {
            return clientVersion
        }

        val versionEnd = clientVersion.lastIndexOf('.', commitHashIndex - 1)
        if (versionEnd <= hyphenIndex) {
            return clientVersion.substring(hyphenIndex + 1)
        }

        return clientVersion.substring(hyphenIndex + 1, versionEnd)
    }
}