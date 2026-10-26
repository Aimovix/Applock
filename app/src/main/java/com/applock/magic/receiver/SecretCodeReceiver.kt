package com.applock.magic.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.applock.magic.ui.MainActivity

class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == "android_secret_code" ||
            action == "android.telephony.action.SECRET_CODE" ||
            action == "android.provider.Telephony.SECRET_CODE"
        ) {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(openIntent)
        }
    }
}
