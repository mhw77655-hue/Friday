package com.jarvis.app.companioncore.engine

/** Compatibility alias for mapper fixtures; production state comes from FridayRuntimeBinding. */
@Deprecated("Use FridayRuntimeSnapshot; this state is not read from Human Core.")
typealias RawCoreState = FridayRuntimeSnapshot
