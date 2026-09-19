package com.jarvis.app.identity.owner

import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.protocol.OwnerBinding

/**
 * Production [OwnerBindingPort] over the REAL `HumanCore` singleton facade.
 * Binds a successful biometric authorization result to the real Human Core
 * owner relationship id (`OwnerBinding.OWNER_RELATIONSHIP_ID`), persisted by
 * the real relationship store.
 */
class HumanCoreOwnerBindingPort : OwnerBindingPort {
    override fun bindOwner(binding: OwnerBinding): Boolean = HumanCore.bindOwner(binding)

    override fun ownerBinding(): OwnerBinding? = HumanCore.ownerBinding()
}