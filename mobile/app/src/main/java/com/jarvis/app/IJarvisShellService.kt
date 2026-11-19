package com.jarvis.app

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/**
 * Narrow Binder contract for Friday's Shizuku UserService.
 * It deliberately exposes typed diagnostics only: never a caller-supplied shell command.
 */
interface IJarvisShellService : IInterface {
    fun diagnosticsSnapshot(): String
    fun captureScreenSummary(): String
    fun setDozeWhitelist(enabled: Boolean): String
    fun extractPackageApks(packageName: String): String
    fun destroy()

    abstract class Stub : Binder(), IJarvisShellService {
        init { attachInterface(this, DESCRIPTOR) }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                TRANSACTION_DIAGNOSTICS -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = diagnosticsSnapshot()
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                TRANSACTION_CAPTURE_SCREEN -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = captureScreenSummary()
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                TRANSACTION_DOZE_WHITELIST -> {
                    data.enforceInterface(DESCRIPTOR)
                    val enabled = data.readInt() != 0
                    val result = setDozeWhitelist(enabled)
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                // Shizuku UserService destruction transaction for a hand-written Binder contract.
                TRANSACTION_EXTRACT_PACKAGE_APKS -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = extractPackageApks(data.readString().orEmpty())
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                SHIZUKU_DESTROY_TRANSACTION -> {
                    destroy()
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }

        companion object {
            const val DESCRIPTOR = "com.jarvis.app.IJarvisShellService"
            const val TRANSACTION_DIAGNOSTICS = IBinder.FIRST_CALL_TRANSACTION
            const val TRANSACTION_CAPTURE_SCREEN = IBinder.FIRST_CALL_TRANSACTION + 1
            const val TRANSACTION_DOZE_WHITELIST = IBinder.FIRST_CALL_TRANSACTION + 2
            const val TRANSACTION_EXTRACT_PACKAGE_APKS = IBinder.FIRST_CALL_TRANSACTION + 3
            const val SHIZUKU_DESTROY_TRANSACTION = 16777114

            @JvmStatic
            fun asInterface(binder: IBinder?): IJarvisShellService? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                return if (local is IJarvisShellService) local else Proxy(binder)
            }
        }
    }

    class Proxy(private val remote: IBinder) : IJarvisShellService {
        override fun asBinder(): IBinder = remote

        override fun diagnosticsSnapshot(): String {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(Stub.DESCRIPTOR)
                remote.transact(Stub.TRANSACTION_DIAGNOSTICS, data, reply, 0)
                reply.readException()
                reply.readString() ?: "{}"
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun captureScreenSummary(): String {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(Stub.DESCRIPTOR)
                remote.transact(Stub.TRANSACTION_CAPTURE_SCREEN, data, reply, 0)
                reply.readException()
                reply.readString() ?: ""
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun setDozeWhitelist(enabled: Boolean): String {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(Stub.DESCRIPTOR)
                data.writeInt(if (enabled) 1 else 0)
                remote.transact(Stub.TRANSACTION_DOZE_WHITELIST, data, reply, 0)
                reply.readException()
                reply.readString() ?: "Doze whitelist result unavailable."
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun extractPackageApks(packageName: String): String {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(Stub.DESCRIPTOR)
                data.writeString(packageName)
                remote.transact(Stub.TRANSACTION_EXTRACT_PACKAGE_APKS, data, reply, 0)
                reply.readException()
                reply.readString() ?: "{}"
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun destroy() {
            remote.transact(Stub.SHIZUKU_DESTROY_TRANSACTION, Parcel.obtain(), null, IBinder.FLAG_ONEWAY)
        }
    }
}
