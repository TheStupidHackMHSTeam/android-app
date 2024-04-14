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

class Guardian : Service() {
    override fun onCreate() {
        Positioning.initiate(this)
        Detector.instance(this)
        Sampler.instance(this)
        Alarm.instance(this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = resources.getString(R.string.app)
        val channelName = "$channelId Background Service"
        val channel = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent, flags: Int, startID: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        internal fun initiate(context: Context) {
            val intent = Intent(context, Guardian::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        internal fun say(context: Context, level: Int, tag: String, message: String) {
            Log.println(level, tag, message)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}