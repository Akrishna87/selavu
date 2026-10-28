package io.github.akrishna87.selavu.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.akrishna87.selavu.MainActivity
import io.github.akrishna87.selavu.R
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.billsDueWithin
import io.github.akrishna87.selavu.core.shortDate
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Once a day, notifies about credit card bills with something still to pay that are due within
 * three days or overdue. Runs on the phone from the saved ledger; nothing goes online.
 */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ledger = Store(applicationContext).load()
        if (!ledger.settings.billReminders) return Result.success()
        val today = LocalDate.now()
        ledger.billsDueWithin(DAYS_BEFORE, today).forEach { (account, bill) ->
            val days = bill.daysLeft(today)
            val amount = Money.format(bill.remaining, account.currency)
            val text = when {
                days < 0 -> "$amount was due on ${shortDate(bill.dueDate, today)}."
                days == 0L -> "$amount is due today."
                days == 1L -> "$amount is due tomorrow."
                else -> "$amount is due on ${shortDate(bill.dueDate, today)}."
            }
            notify(applicationContext, account.id.hashCode(), "${account.name} bill", text)
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "card-bill-reminders"
        private const val CHANNEL = "bills"
        const val DAYS_BEFORE = 3L

        /** Starts or stops the daily check to match the setting. */
        fun schedule(context: Context, on: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (on) {
                wm.enqueueUniquePeriodicWork(
                    WORK, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS).build(),
                )
            } else {
                wm.cancelUniqueWork(WORK)
            }
        }

        private fun notify(context: Context, id: Int, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Card bills", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(id, n)
        }
    }
}
