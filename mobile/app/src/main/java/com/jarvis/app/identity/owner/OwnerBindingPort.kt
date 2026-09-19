package com.jarvis.app.identity.owner

import com.jarvis.app.humancore.protocol.OwnerBinding

/**
 * The persistence seam for the owner-binding chain: writes an [OwnerBinding]
 * into the real Human Core owner relationship and reads the current binding.
 *
 * Production implements this over the `HumanCore` facade
 * ([HumanCoreOwnerBindingPort]); tests implement it over a real
 * [com.jarvis.app.humancore.HumanCoreGraph] backed by isolated storage so the
 * fixture can prove the chain end-to-end.
 */
interface OwnerBindingPort {
    /** Persist the binding; false when the store refused it. */
    fun bindOwner(binding: OwnerBinding): Boolean

    /** The currently persisted owner binding, or null when unbound. */
    fun ownerBinding(): OwnerBinding?
}