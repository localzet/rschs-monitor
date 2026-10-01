package dev.localzet.rschs
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
class SmsReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {
  if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
  Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { sms ->
   AlertStore.add(context, sms.originatingAddress ?: "", sms.messageBody ?: "", sms.timestampMillis)
  }
  context.sendBroadcast(Intent(MainActivity.ACTION_REFRESH).setPackage(context.packageName))
 }
}