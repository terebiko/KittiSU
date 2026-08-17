package anhiutangerinee.kittisu.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndPager() = rule.collect(
        packageName = "anhiutangerinee.kittisu",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        val y = device.displayHeight / 2
        val edge = device.displayWidth / 5
        repeat(3) {
            device.swipe(device.displayWidth - edge, y, edge, y, 20)
            device.waitForIdle()
        }
    }
}
