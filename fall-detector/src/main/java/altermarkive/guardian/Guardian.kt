package altermarkive.guardian

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class Guardian : Service() {
    override fun onCreate() {
        Positioning.initiate(this)
        Detector.instance(this)
        Sampler.instance(this)
        Alarm.instance(this)
    }

    private fun createLowPriorityNotificationChannel(): String {
        val channelId = resources.getString(R.string.app)
        val channelName = "$channelId Background Service"
        val channel = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_MIN
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent, flags: Int, startID: Int): Int {
        val channelId = createLowPriorityNotificationChannel()
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app))
            .setContentText(getString(R.string.guardian))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setVibrate(null)
            .setSound(null)
            .setLights(0, 0, 0)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()
        startForeground(1, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        internal fun initiate(context: Context) {
            val intent = Intent(context, Guardian::class.java)
            context.startForegroundService(intent)
        }

        internal fun say(context: Context, level: Int, tag: String, message: String) {
            Log.println(level, tag, message)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}