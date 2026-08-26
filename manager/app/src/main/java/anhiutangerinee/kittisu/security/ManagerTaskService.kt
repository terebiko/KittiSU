package anhiutangerinee.kittisu.security

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Lightweight service kept alive while the manager is unlocked so that
 * [onTaskRemoved] fires when the user swipes the task away from recents,
 * letting us close the dynamic manager session immediately.
 * Kernel owner-liveness covers process death without this callback.
 */
class ManagerTaskService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        ManagerSecurity.onTaskRemoved()
        super.onTaskRemoved(rootIntent)
    }
}
