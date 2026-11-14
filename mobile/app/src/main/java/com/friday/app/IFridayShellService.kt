package com.friday.app

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/**
 * Hand-written Binder interface, standing in for what an .aidl file would
 * normally generate. This device's toolchain has no working `aidl`
 * compiler (no real build-tools install — only aapt2 is fetched via
 * Maven), so this is written directly against the same IBinder/Parcel
 * primitives AIDL itself compiles down to. Functionally identical to a
 * generated Stub/Proxy pair.
 */
interface IFridayShellService : IInterface {
    fun exec(command: String): String
    fun destroy()

    abstract class Stub : Binder(), IFridayShellService {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                TRANSACTION_exec -> {
                    data.enforceInterface(DESCRIPTOR)
                    val command = data.readString() ?: ""
                    val result = exec(command)
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                TRANSACTION_destroy -> {
                    data.enforceInterface(DESCRIPTOR)
                    destroy()
                    reply?.writeNoException()
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }

        companion object {
            const val DESCRIPTOR = "com.friday.app.IFridayShellService"
            const val TRANSACTION_exec = IBinder.FIRST_CALL_TRANSACTION
            const val TRANSACTION_destroy = IBinder.FIRST_CALL_TRANSACTION + 1

            @JvmStatic
            fun asInterface(binder: IBinder?): IFridayShellService? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                if (local is IFridayShellService) return local
                return Proxy(binder)
            }
        }
    }

    class Proxy(private val remote: IBinder) : IFridayShellService {
        override fun asBinder(): IBinder = remote

        override fun exec(command: String): String {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(Stub.DESCRIPTOR)
                data.writeString(command)
                remote.transact(Stub.TRANSACTION_exec, data, reply, 0)
                reply.readException()
                reply.readString() ?: ""
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun destroy() {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(Stub.DESCRIPTOR)
                remote.transact(Stub.TRANSACTION_destroy, data, reply, 0)
                reply.readException()
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
