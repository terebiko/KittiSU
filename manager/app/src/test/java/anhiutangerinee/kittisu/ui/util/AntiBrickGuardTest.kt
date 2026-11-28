package anhiutangerinee.kittisu.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiBrickGuardTest {

    @Test
    fun blocksDdZeroToCriticalPartitions() {
        val dangerous = listOf(
            "dd if=/dev/zero of=/dev/block/by-name/xbl_a",
            "dd if=/dev/zero of=/dev/block/bootdevice/by-name/abl",
            "dd if=/dev/urandom of=/dev/block/by-name/tz_b",
            "dd if=/dev/zero of=/dev/block/by-name/ocdt",
            "dd if=/dev/zero of=/dev/block/by-name/bootloader",
            "dd if=/dev/zero of=/dev/block/by-name/preloader",
            "dd if=/dev/zero of=/dev/block/sda",
            "dd if=/dev/zero of=/dev/block/mmcblk0",
        )
        dangerous.forEach { cmd ->
            val res = AntiBrickGuard.scanText("test.sh", cmd)
            assertTrue("Expected blocked: $cmd", res.isDangerous)
        }
    }

    @Test
    fun blocksDestructiveRmWipeAndEdl() {
        val dangerous = listOf(
            "rm -rf /",
            "rm -rf /*",
            "rm -rf /data",
            "rm -rf /system/*",
            "rm -rf /persist",
            "rm -rf /dev/block/by-name/efs",
            "wipefs -a /dev/block/sda",
            "blkdiscard /dev/block/by-name/userdata",
            "mkfs.ext4 /dev/block/by-name/metadata",
            "sgdisk --zap-all /dev/block/sda",
            "cat /dev/zero > /dev/block/by-name/xbl",
            "echo 0 > /dev/block/by-name/frp",
            "reboot edl",
            "setprop sys.powerctl reboot,edl",
            "echo ZGQgaWY9L2Rldi96ZXJv | base64 -d | sh",
        )
        dangerous.forEach { cmd ->
            val res = AntiBrickGuard.scanText("brick.sh", cmd)
            assertTrue("Expected blocked: $cmd", res.isDangerous)
        }
    }

    @Test
    fun allowsSafeCommandsAndReadOnlyBackups() {
        val safe = listOf(
            "# dd if=/dev/zero of=/dev/block/by-name/xbl",
            "ls -la /data/adb/modules",
            "getprop ro.build.version.release",
            "dd if=/dev/block/by-name/boot_a of=/sdcard/boot_backup.img",
            "rm -rf /data/local/tmp/my_temp_dir",
            "echo hello > /data/local/tmp/test.txt",
            "cat /proc/version",
            "pm list packages -3",
        )
        safe.forEach { cmd ->
            val res = AntiBrickGuard.scanText("safe.sh", cmd)
            assertFalse("Expected safe but blocked: $cmd -> ${res.findings}", res.isDangerous)
        }
    }
}
