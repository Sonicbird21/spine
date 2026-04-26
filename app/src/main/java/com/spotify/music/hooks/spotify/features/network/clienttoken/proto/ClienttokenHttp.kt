package com.spotify.music.hooks.spotify.features.network.clienttoken.proto

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.text.Charsets

object ClienttokenHttp {
    private interface ProtoMessage {
        fun toByteArray(): ByteArray
    }

    private class ProtoWriter {
        private val outputStream = ByteArrayOutputStream()

        fun writeInt32(fieldNumber: Int, value: Int) {
            if (value == 0) return
            writeTag(fieldNumber, 0)
            writeVarint32(value)
        }

        fun writeBool(fieldNumber: Int, value: Boolean) {
            if (!value) return
            writeTag(fieldNumber, 0)
            writeVarint32(1)
        }

        fun writeString(fieldNumber: Int, value: String) {
            if (value.isEmpty()) return
            writeTag(fieldNumber, 2)
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeVarint32(bytes.size)
            outputStream.write(bytes)
        }

        fun writeMessage(fieldNumber: Int, value: ProtoMessage) {
            val bytes = value.toByteArray()
            if (bytes.isEmpty()) return
            writeTag(fieldNumber, 2)
            writeVarint32(bytes.size)
            outputStream.write(bytes)
        }

        fun toByteArray(): ByteArray = outputStream.toByteArray()

        private fun writeTag(fieldNumber: Int, wireType: Int) {
            writeVarint32((fieldNumber shl 3) or wireType)
        }

        private fun writeVarint32(value: Int) {
            var current = value
            while (current and -0x80 != 0) {
                outputStream.write(current and 0x7f or 0x80)
                current = current ushr 7
            }
            outputStream.write(current and 0x7f)
        }
    }

    private class ProtoReader(sourceBytes: ByteArray) {
        private val bytes = sourceBytes
        private var index = 0

        fun readTag(): Int {
            return if (index >= bytes.size) 0 else readVarint32()
        }

        fun readVarint32(): Int {
            var result = 0
            var shift = 0

            while (shift < 32) {
                val next = readRawByte()
                result = result or ((next and 0x7f) shl shift)
                if (next and 0x80 == 0) return result
                shift += 7
            }

            throw IOException("Malformed varint")
        }

        fun readBytes(): ByteArray {
            val length = readVarint32()
            if (length <= 0) return ByteArray(0)
            if (index + length > bytes.size) throw IOException("Unexpected end of protobuf stream")
            val value = bytes.copyOfRange(index, index + length)
            index += length
            return value
        }

        fun readString(): String = String(readBytes(), Charsets.UTF_8)

        fun readBool(): Boolean = readVarint32() != 0

        fun skipField(tag: Int) {
            when (tag and 7) {
                0 -> readVarint32()
                1 -> skipRawBytes(8)
                2 -> skipRawBytes(readVarint32())
                5 -> skipRawBytes(4)
                else -> throw IOException("Unsupported wire type ${(tag and 7)}")
            }
        }

        private fun skipRawBytes(length: Int) {
            if (length <= 0) return
            if (index + length > bytes.size) throw IOException("Unexpected end of protobuf stream")
            index += length
        }

        private fun readRawByte(): Int {
            if (index >= bytes.size) throw IOException("Unexpected end of protobuf stream")
            return bytes[index++].toInt() and 0xff
        }
    }

    enum class ClientTokenRequestType(val value: Int) {
        REQUEST_UNKNOWN(0),
        REQUEST_CLIENT_DATA_REQUEST(1),
        REQUEST_CHALLENGE_ANSWERS_REQUEST(2),
        ;

        companion object {
            fun fromValue(value: Int): ClientTokenRequestType {
                return entries.firstOrNull { it.value == value } ?: REQUEST_UNKNOWN
            }
        }
    }

    enum class ClientTokenResponseType(val value: Int) {
        RESPONSE_UNKNOWN(0),
        RESPONSE_GRANTED_TOKEN_RESPONSE(1),
        RESPONSE_CHALLENGES_RESPONSE(2),
        ;

        companion object {
            fun fromValue(value: Int): ClientTokenResponseType {
                return entries.firstOrNull { it.value == value } ?: RESPONSE_UNKNOWN
            }
        }
    }

    data class ClientTokenRequest(
        val requestType: ClientTokenRequestType = ClientTokenRequestType.REQUEST_UNKNOWN,
        val clientData: ClientDataRequest? = null,
        val challengeAnswers: ChallengeAnswersRequest? = null,
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeInt32(1, requestType.value)
                clientData?.let { writeMessage(2, it) }
                challengeAnswers?.let { writeMessage(3, it) }
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): ClientTokenRequest = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): ClientTokenRequest {
                val reader = ProtoReader(bytes)
                var requestType = ClientTokenRequestType.REQUEST_UNKNOWN
                var clientData: ClientDataRequest? = null
                var challengeAnswers: ChallengeAnswersRequest? = null

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return ClientTokenRequest(requestType, clientData, challengeAnswers)
                        8 -> requestType = ClientTokenRequestType.fromValue(reader.readVarint32())
                        18 -> clientData = ClientDataRequest.parseFrom(reader.readBytes())
                        26 -> challengeAnswers = ChallengeAnswersRequest.parseFrom(reader.readBytes())
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class ClientDataRequest(
        val clientVersion: String = "",
        val clientId: String = "",
        val currentToken: String = "",
        val connectivitySdkData: ConnectivitySdkData? = null,
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeString(1, clientVersion)
                writeString(2, clientId)
                writeString(5, currentToken)
                connectivitySdkData?.let { writeMessage(3, it) }
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): ClientDataRequest = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): ClientDataRequest {
                val reader = ProtoReader(bytes)
                var clientVersion = ""
                var clientId = ""
                var currentToken = ""
                var connectivitySdkData: ConnectivitySdkData? = null

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return ClientDataRequest(clientVersion, clientId, currentToken, connectivitySdkData)
                        10 -> clientVersion = reader.readString()
                        18 -> clientId = reader.readString()
                        26 -> connectivitySdkData = ConnectivitySdkData.parseFrom(reader.readBytes())
                        42 -> currentToken = reader.readString()
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class ChallengeAnswersRequest(
        val state: String = "",
        val answers: List<ChallengeAnswer> = emptyList(),
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeString(1, state)
                answers.forEach { writeMessage(2, it) }
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): ChallengeAnswersRequest = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): ChallengeAnswersRequest {
                val reader = ProtoReader(bytes)
                var state = ""
                val answers = mutableListOf<ChallengeAnswer>()

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return ChallengeAnswersRequest(state, answers)
                        10 -> state = reader.readString()
                        18 -> answers += ChallengeAnswer.parseFrom(reader.readBytes())
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class ChallengeAnswer(
        val challengeType: ChallengeType = ChallengeType.CHALLENGE_UNKNOWN,
        val clientSecret: ClientSecretHMACAnswer? = null,
        val evaluateJs: EvaluateJSAnswer? = null,
        val hashCash: HashCashAnswer? = null,
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeInt32(1, challengeType.value)
                clientSecret?.let { writeMessage(2, it) }
                evaluateJs?.let { writeMessage(3, it) }
                hashCash?.let { writeMessage(4, it) }
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): ChallengeAnswer = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): ChallengeAnswer {
                val reader = ProtoReader(bytes)
                var challengeType = ChallengeType.CHALLENGE_UNKNOWN
                var clientSecret: ClientSecretHMACAnswer? = null
                var evaluateJs: EvaluateJSAnswer? = null
                var hashCash: HashCashAnswer? = null

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return ChallengeAnswer(challengeType, clientSecret, evaluateJs, hashCash)
                        8 -> challengeType = ChallengeType.fromValue(reader.readVarint32())
                        18 -> clientSecret = ClientSecretHMACAnswer.parseFrom(reader.readBytes())
                        26 -> evaluateJs = EvaluateJSAnswer.parseFrom(reader.readBytes())
                        34 -> hashCash = HashCashAnswer.parseFrom(reader.readBytes())
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class ClientSecretHMACAnswer(
        val hmac: String = "",
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeString(1, hmac)
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): ClientSecretHMACAnswer = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): ClientSecretHMACAnswer {
                val reader = ProtoReader(bytes)
                var hmac = ""

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return ClientSecretHMACAnswer(hmac)
                        10 -> hmac = reader.readString()
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class EvaluateJSAnswer(
        val result: String = "",
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeString(1, result)
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): EvaluateJSAnswer = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): EvaluateJSAnswer {
                val reader = ProtoReader(bytes)
                var result = ""

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return EvaluateJSAnswer(result)
                        10 -> result = reader.readString()
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class HashCashAnswer(
        val suffix: String = "",
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeString(1, suffix)
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): HashCashAnswer = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): HashCashAnswer {
                val reader = ProtoReader(bytes)
                var suffix = ""

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return HashCashAnswer(suffix)
                        10 -> suffix = reader.readString()
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    enum class ChallengeType(val value: Int) {
        CHALLENGE_UNKNOWN(0),
        CHALLENGE_CLIENT_SECRET_HMAC(1),
        CHALLENGE_EVALUATE_JS(2),
        CHALLENGE_HASH_CASH(3),
        ;

        companion object {
            fun fromValue(value: Int): ChallengeType {
                return entries.firstOrNull { it.value == value } ?: CHALLENGE_UNKNOWN
            }
        }
    }

    data class ConnectivitySdkData(
        val platformSpecificData: PlatformSpecificData? = null,
        val deviceId: String = "",
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                platformSpecificData?.let { writeMessage(1, it) }
                writeString(2, deviceId)
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): ConnectivitySdkData = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): ConnectivitySdkData {
                val reader = ProtoReader(bytes)
                var platformSpecificData: PlatformSpecificData? = null
                var deviceId = ""

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return ConnectivitySdkData(platformSpecificData, deviceId)
                        10 -> platformSpecificData = PlatformSpecificData.parseFrom(reader.readBytes())
                        18 -> deviceId = reader.readString()
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class PlatformSpecificData(
        val ios: NativeIOSData? = null,
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                ios?.let { writeMessage(2, it) }
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): PlatformSpecificData = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): PlatformSpecificData {
                val reader = ProtoReader(bytes)
                var ios: NativeIOSData? = null

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return PlatformSpecificData(ios)
                        18 -> ios = NativeIOSData.parseFrom(reader.readBytes())
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class NativeIOSData(
        val userInterfaceIdiom: Int = 0,
        val targetIphoneSimulator: Boolean = false,
        val hwMachine: String = "",
        val systemVersion: String = "",
        val simulatorModelIdentifier: String = "",
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeInt32(1, userInterfaceIdiom)
                writeBool(2, targetIphoneSimulator)
                writeString(3, hwMachine)
                writeString(4, systemVersion)
                writeString(5, simulatorModelIdentifier)
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): NativeIOSData = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): NativeIOSData {
                val reader = ProtoReader(bytes)
                var userInterfaceIdiom = 0
                var targetIphoneSimulator = false
                var hwMachine = ""
                var systemVersion = ""
                var simulatorModelIdentifier = ""

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return NativeIOSData(
                            userInterfaceIdiom,
                            targetIphoneSimulator,
                            hwMachine,
                            systemVersion,
                            simulatorModelIdentifier,
                        )
                        8 -> userInterfaceIdiom = reader.readVarint32()
                        16 -> targetIphoneSimulator = reader.readBool()
                        26 -> hwMachine = reader.readString()
                        34 -> systemVersion = reader.readString()
                        42 -> simulatorModelIdentifier = reader.readString()
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class ClientTokenResponse(
        val responseType: ClientTokenResponseType = ClientTokenResponseType.RESPONSE_UNKNOWN,
        val grantedToken: GrantedTokenResponse? = null,
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeInt32(1, responseType.value)
                grantedToken?.let { writeMessage(2, it) }
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): ClientTokenResponse = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): ClientTokenResponse {
                val reader = ProtoReader(bytes)
                var responseType = ClientTokenResponseType.RESPONSE_UNKNOWN
                var grantedToken: GrantedTokenResponse? = null

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return ClientTokenResponse(responseType, grantedToken)
                        8 -> responseType = ClientTokenResponseType.fromValue(reader.readVarint32())
                        18 -> grantedToken = GrantedTokenResponse.parseFrom(reader.readBytes())
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class GrantedTokenResponse(
        val token: String = "",
        val expiresAfterSeconds: Int = 0,
        val refreshAfterSeconds: Int = 0,
        val domains: List<TokenDomain> = emptyList(),
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeString(1, token)
                writeInt32(2, expiresAfterSeconds)
                writeInt32(3, refreshAfterSeconds)
                domains.forEach { writeMessage(4, it) }
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): GrantedTokenResponse = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): GrantedTokenResponse {
                val reader = ProtoReader(bytes)
                var token = ""
                var expiresAfterSeconds = 0
                var refreshAfterSeconds = 0
                val domains = mutableListOf<TokenDomain>()

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return GrantedTokenResponse(token, expiresAfterSeconds, refreshAfterSeconds, domains)
                        10 -> token = reader.readString()
                        16 -> expiresAfterSeconds = reader.readVarint32()
                        24 -> refreshAfterSeconds = reader.readVarint32()
                        34 -> domains += TokenDomain.parseFrom(reader.readBytes())
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }

    data class TokenDomain(
        val domain: String = "",
    ) : ProtoMessage {
        override fun toByteArray(): ByteArray {
            return ProtoWriter().apply {
                writeString(1, domain)
            }.toByteArray()
        }

        companion object {
            fun parseFrom(inputStream: InputStream): TokenDomain = parseFrom(inputStream.readBytes())

            fun parseFrom(bytes: ByteArray): TokenDomain {
                val reader = ProtoReader(bytes)
                var domain = ""

                while (true) {
                    when (val tag = reader.readTag()) {
                        0 -> return TokenDomain(domain)
                        10 -> domain = reader.readString()
                        else -> reader.skipField(tag)
                    }
                }
            }
        }
    }
}