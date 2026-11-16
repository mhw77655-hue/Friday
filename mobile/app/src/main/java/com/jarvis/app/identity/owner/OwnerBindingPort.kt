package com.jarvis.app.identity.owner

/** Persistence seam for Friday's Android owner authority record. */
interface OwnerBindingPort {
    /** Persist a binding; false when integrity or single-owner checks fail. */
    fun bindOwner(binding: OwnerBinding): Boolean

    /** Read the encrypted binding, or null when no valid record exists. */
    fun ownerBinding(): OwnerBinding?
}
