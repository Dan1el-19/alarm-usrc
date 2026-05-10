package dev.usrc.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClearSelectedVariantReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logger.d("LIFECYCLE: ClearSelectedVariantReceiver triggered.")
        val repository = SettingsRepository(context)
        repository.updateSettings { 
            it.copy(
                selectedVariantId = null,
                selectedVariantDate = null,
                selectedVariantSource = null,
                retryCount = 0,
                clearStateScheduledAt = null
            )
        }
        Logger.d("LIFECYCLE: Active selection cleared successfully.")
    }
}
