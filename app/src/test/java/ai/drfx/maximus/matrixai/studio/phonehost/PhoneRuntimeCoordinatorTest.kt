package ai.drfx.maximus.matrixai.studio.phonehost

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PhoneRuntimeCoordinatorTest {
    @Test fun installBuildAndShellCannotMutateTheGuestSimultaneously() {
        for (operation in PhoneRuntimeCoordinator.Operation.entries) {
            val lease = PhoneRuntimeCoordinator.acquire(operation)
            try {
                for (other in PhoneRuntimeCoordinator.Operation.entries) {
                    assertThrows(IllegalStateException::class.java) { PhoneRuntimeCoordinator.acquire(other) }
                }
                assertEquals(operation, PhoneRuntimeCoordinator.current())
            } finally { lease.close() }
        }
        assertNull(PhoneRuntimeCoordinator.current())
    }
    @Test fun staleCleanupCannotReleaseAnotherOperationsLease() {
        val first = PhoneRuntimeCoordinator.acquire(PhoneRuntimeCoordinator.Operation.BUILD)
        first.close()
        val second = PhoneRuntimeCoordinator.acquire(PhoneRuntimeCoordinator.Operation.LINUX)
        try {
            first.close()
            assertEquals(PhoneRuntimeCoordinator.Operation.LINUX, PhoneRuntimeCoordinator.current())
        } finally { second.close() }
    }
    @Test fun parallelAcquisitionHasOnlyOneOwner() {
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        val leases = java.util.Collections.synchronizedList(mutableListOf<PhoneRuntimeCoordinator.Lease>())
        try {
            val attempts = (1..8).map {
                pool.submit(Callable {
                    start.await()
                    runCatching { PhoneRuntimeCoordinator.acquire(PhoneRuntimeCoordinator.Operation.BUILD) }
                        .getOrNull()?.let { leases.add(it) }
                    done.countDown()
                })
            }
            start.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            attempts.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, leases.size)
        } finally { leases.forEach { it.close() }; pool.shutdownNow() }
    }
}
