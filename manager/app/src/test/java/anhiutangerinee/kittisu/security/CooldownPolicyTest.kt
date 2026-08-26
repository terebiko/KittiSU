package anhiutangerinee.kittisu.security

import org.junit.Assert.assertEquals
import org.junit.Test

class CooldownPolicyTest {
    @Test
    fun nextCooldown_startsAtAttemptLimitThenGrowsExponentially() {
        assertEquals(Cooldown(level = 0, durationMillis = 0), CooldownPolicy.next(2, 3, 0))
        assertEquals(Cooldown(level = 1, durationMillis = 30_000), CooldownPolicy.next(3, 3, 0))
        assertEquals(Cooldown(level = 2, durationMillis = 60_000), CooldownPolicy.next(4, 3, 1))
        assertEquals(Cooldown(level = 3, durationMillis = 120_000), CooldownPolicy.next(5, 3, 2))
    }

    @Test
    fun nextCooldown_capsAtTenMinutesWithoutOverflow() {
        assertEquals(Cooldown(level = 6, durationMillis = 600_000), CooldownPolicy.next(10, 3, 5))
        assertEquals(Cooldown(level = Int.MAX_VALUE, durationMillis = 600_000), CooldownPolicy.next(10, 3, Int.MAX_VALUE))
    }

    @Test
    fun clearFailures_resetsAllCooldownStateButPreservesLockdown() {
        val runtime = LockRuntime(8, 4, 999_999, "boot-id", lockdown = true)

        assertEquals(LockRuntime(lockdown = true), runtime.clearFailures())
    }
}
