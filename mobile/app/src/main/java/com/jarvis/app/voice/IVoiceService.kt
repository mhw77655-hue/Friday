package com.jarvis.app.voice

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

/**
 * R2 — the Binder contract between the app process and the `:voice` habitat.
 *
 * Hand-written (no AIDL compiler on this toolchain), modeled on the R1
 * contract but upgraded from placeholder bytes to real synthesis:
 *
 *   - [synthesize] writes a 16-bit PCM WAV to [outPath] and returns a JSON
 *     result — audio does NOT cross the Binder (transaction cap ~1 MB); both
 *     processes share a UID, so the app reads the habitat-written file.
 *   - [cancel] stops an in-flight synthesis (habitat-side).
 *   - [getCapabilities] returns the provider/model capability snapshot as JSON.
 *
 * This is the seam a future environment (Termux/remote) plugs into without
 * touching the nervous system.
 */
interface IVoiceService : IInterface {

    fun ping(): Long

    /**
     * Synthesize [text] in [language] with an optional [voiceId] (empty = let
     * the habitat choose), honoring [optionsJson] (VoiceSynthesisParams).
     * Writes audio to [outPath]; returns a JSON string:
     * `{"success":bool,"sampleRate":int,"channels":int,"durationMs":long,"error":string|null}`.
     */
    fun synthesize(text: String, language: String, voiceId: String, optionsJson: String, outPath: String): String

    /** Stop the current synthesis. Returns true if anything was cancelled. */
    fun cancel(): Boolean

    /** Capability snapshot as JSON (providers, voices, languages, model state). */
    fun getCapabilities(): String

    fun getStatus(): String

    fun crashNow()

    abstract class Stub : Binder(), IVoiceService {

        init {
            attachInterface(this, Stub.DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                Stub.TRANSACTION_ping -> {
                    data.enforceInterface(Stub.DESCRIPTOR)
                    val result = ping()
                    reply?.writeNoException()
                    reply?.writeLong(result)
                    return true
                }
                Stub.TRANSACTION_synthesize -> {
                    data.enforceInterface(Stub.DESCRIPTOR)
                    val text = data.readString() ?: ""
                    val language = data.readString() ?: ""
                    val voiceId = data.readString() ?: ""
                    val optionsJson = data.readString() ?: "{}"
                    val outPath = data.readString() ?: ""
                    val result = synthesize(text, language, voiceId, optionsJson, outPath)
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                Stub.TRANSACTION_cancel -> {
                    data.enforceInterface(Stub.DESCRIPTOR)
                    val result = cancel()
                    reply?.writeNoException()
                    reply?.writeBoolean(result)
                    return true
                }
                Stub.TRANSACTION_getCapabilities -> {
                    data.enforceInterface(Stub.DESCRIPTOR)
                    val result = getCapabilities()
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                Stub.TRANSACTION_getStatus -> {
                    data.enforceInterface(Stub.DESCRIPTOR)
                    val result = getStatus()
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                Stub.TRANSACTION_crashNow -> {
                    data.enforceInterface(Stub.DESCRIPTOR)
                    crashNow()
                    reply?.writeNoException()
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }

        companion object {
            const val DESCRIPTOR = "com.jarvis.app.voice.IVoiceService"
            const val TRANSACTION_ping = IBinder.FIRST_CALL_TRANSACTION
            const val TRANSACTION_synthesize = IBinder.FIRST_CALL_TRANSACTION + 1
            const val TRANSACTION_getStatus = IBinder.FIRST_CALL_TRANSACTION + 2
            const val TRANSACTION_crashNow = IBinder.FIRST_CALL_TRANSACTION + 3
            const val TRANSACTION_cancel = IBinder.FIRST_CALL_TRANSACTION + 4
            const val TRANSACTION_getCapabilities = IBinder.FIRST_CALL_TRANSACTION + 5

            @JvmStatic
            fun asInterface(binder: IBinder?): IVoiceService? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                if (local is IVoiceService) return local
                return Proxy(binder)
            }
        }

        class Proxy(private val remote: IBinder) : IVoiceService {
            override fun asBinder(): IBinder = remote

            override fun ping(): Long {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(Stub.DESCRIPTOR)
                    remote.transact(Stub.TRANSACTION_ping, data, reply, 0)
                    reply.readException()
                    reply.readLong()
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }

            override fun synthesize(text: String, language: String, voiceId: String, optionsJson: String, outPath: String): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(Stub.DESCRIPTOR)
                    data.writeString(text)
                    data.writeString(language)
                    data.writeString(voiceId)
                    data.writeString(optionsJson)
                    data.writeString(outPath)
                    remote.transact(Stub.TRANSACTION_synthesize, data, reply, 0)
                    reply.readException()
                    reply.readString() ?: """{"success":false,"error":"empty response"}"""
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }

            override fun cancel(): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(Stub.DESCRIPTOR)
                    remote.transact(Stub.TRANSACTION_cancel, data, reply, 0)
                    reply.readException()
                    reply.readBoolean()
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }

            override fun getCapabilities(): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(Stub.DESCRIPTOR)
                    remote.transact(Stub.TRANSACTION_getCapabilities, data, reply, 0)
                    reply.readException()
                    reply.readString() ?: "{}"
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }

            override fun getStatus(): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(Stub.DESCRIPTOR)
                    remote.transact(Stub.TRANSACTION_getStatus, data, reply, 0)
                    reply.readException()
                    reply.readString() ?: ""
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }

            override fun crashNow() {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(Stub.DESCRIPTOR)
                    remote.transact(Stub.TRANSACTION_crashNow, data, reply, 0)
                    reply.readException()
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }
}
