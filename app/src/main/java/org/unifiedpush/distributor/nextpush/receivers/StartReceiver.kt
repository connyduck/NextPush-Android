package org.unifiedpush.distributor.nextpush.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.unifiedpush.distributor.nextpush.services.RestartWorker

class StartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        booting = true
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            RestartWorker.startPeriodic(context)
        }
    }

    companion object {
        var booting = false
    }
}
