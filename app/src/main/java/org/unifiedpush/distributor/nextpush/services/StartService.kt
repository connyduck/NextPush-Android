package org.unifiedpush.distributor.nextpush.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import org.unifiedpush.distributor.nextpush.account.AccountUtils.nextcloudAppNotInstalledDialog
import org.unifiedpush.distributor.nextpush.account.AccountUtils.ssoAccount
import org.unifiedpush.distributor.nextpush.api.Api.apiDestroy
import org.unifiedpush.distributor.nextpush.api.Api.apiSync
import org.unifiedpush.distributor.nextpush.api.SSEListener.Companion.lastEventDate
import org.unifiedpush.distributor.nextpush.utils.NOTIFICATION_ID_FOREGROUND
import org.unifiedpush.distributor.nextpush.utils.NotificationUtils.createForegroundNotification
import org.unifiedpush.distributor.nextpush.utils.TAG

class StartService : Service() {

    private val networkCallback = RestartNetworkCallback(this)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        service = this
        Log.i(TAG, "StartService created")
        val notification = createForegroundNotification(this)
        startForeground(NOTIFICATION_ID_FOREGROUND, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        networkCallback.register()
        startService()
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying Service")
        apiDestroy()
        wakeLock?.let {
            while (it.isHeld) {
                it.release()
            }
        }
        if (isServiceStarted) {
            Log.d(TAG, "onDestroy: restarting worker")
            RestartWorker.start(this, delay = 0)
        }
    }

    private fun startService() {
        // If the service is running and we don't have any fail
        // In case somehow startService is called when everything is fine
        if (isServiceStarted && !FailureHandler.hasFailed()) return
        isServiceStarted = true

        try {
            ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(this)
        } catch (e: NextcloudFilesAppAccountNotFoundException) {
            nextcloudAppNotInstalledDialog(this)
        } catch (e: NoCurrentAccountSelectedException) {
            return
        }

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                acquire(10000L /*10 secs*/)
            }
        }

        apiSync()
    }

    companion object StartServiceCompanion {
        private const val WAKE_LOCK_TAG = "NextPush:StartService:lock"

        private var service: StartService? = null
        var isServiceStarted = false
            private set
        var wakeLock: PowerManager.WakeLock? = null
            private set

        fun startListener(context: Context) {
            if (isServiceStarted && !FailureHandler.hasFailed()) return
            Log.d(TAG, "Starting the Listener")
            Log.d(TAG, "Service is started: $isServiceStarted")
            Log.d(TAG, "nFails: ${FailureHandler.nFails}")
            val serviceIntent = Intent(context, StartService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        fun stopService(block: () -> Unit = {}) {
            Log.d(TAG, "Stopping Service")
            isServiceStarted = false
            lastEventDate = null
            service?.stopSelf()
            block()
        }
    }
}
