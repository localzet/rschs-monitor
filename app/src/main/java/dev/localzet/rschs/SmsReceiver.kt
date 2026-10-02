package dev.localzet.rschs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        Thread {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                messages.groupBy { (it.originatingAddress ?: "") to it.timestampMillis }.forEach { (key, parts) ->
                    AlertStore.add(context, key.first, parts.joinToString("") { it.messageBody ?: "" }, key.second)
                }
                context.sendBroadcast(Intent(MainActivity.ACTION_REFRESH).setPackage(context.packageName))
            } finally { pending.finish() }
        }.start()
    }
}