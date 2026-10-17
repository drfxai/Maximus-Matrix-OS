package ai.drfx.maximus.matrixai.studio.phonehost

import java.util.UUID

/** Exclusive ownership of a mutable guest filesystem, including installation. */
internal object PhoneRuntimeCoordinator {
    enum class Operation { INSTALL, VERIFY, BUILD, LINUX }
    class Lease internal constructor(val id: String, val operation: Operation) : AutoCloseable {
        override fun close() = release(this)
    }
    private var active: Lease? = null
    @Synchronized fun acquire(operation: Operation): Lease {
        check(active == null) { "The phone runtime is busy. Stop the active operation first." }
        return Lease(UUID.randomUUID().toString(), operation).also { active = it }
    }
    @Synchronized private fun release(lease: Lease) {
        if (active == lease) active = null
    }
    @Synchronized fun current(): Operation? = active?.operation
}
