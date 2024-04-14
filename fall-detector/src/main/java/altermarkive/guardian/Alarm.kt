package altermarkive.guardian

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.net.HttpURLConnection
import java.net.URL


class Alarm private constructor(val context: Guardian) {
    class CancelReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "CANCEL") {
                Alarm.cancel = true
            }
        }
    }

    private var pool: SoundPool
    private var id: Int

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes: AudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            pool = SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build()
        } else {
            @Suppress("DEPRECATION")
            pool = SoundPool(5, AudioManager.STREAM_ALARM, 0)
        }
        id = pool.load(context.applicationContext, R.raw.alarm, 1)

        val filter = IntentFilter()
        filter.addAction("CANCEL")
        context.registerReceiver(CancelReceiver(), filter)
    }

    companion object {
        private var cancel: Boolean = false
        private var singleton: Alarm? = null
        private lateinit var fusedLocationClient: FusedLocationProviderClient

        internal fun instance(context: Guardian): Alarm {
            var singleton = this.singleton
            if (singleton == null) {
                singleton = Alarm(context)
                this.singleton = singleton
            }
            return singleton
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun siren(context: Context) {
            cancel = false
            loudest(context, AudioManager.STREAM_ALARM)
            val singleton = this.singleton
            if (singleton != null) {
                val pool = singleton.pool
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

                val intent = Intent().apply {
                    action = "CANCEL"
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                val fullScreenIntent = Intent(context, Main::class.java)
                fullScreenIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                val fullScreenPendingIntent = PendingIntent.getActivity(context, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                val builder = NotificationCompat.Builder(context, context.resources.getString(R.string.app))
                builder
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("FALL DETECTED")
                    .setContentText("Fall Detected!!! Clear the notification to stop the alarm.")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(false)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setSilent(false)

                with(NotificationManagerCompat.from(context)) {
                    notify(0, builder.build())
                }

                val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                while (true) {
                    if (cancel) {
                        break
                    }

                    var notifFound = false

                    val notifications = mNotificationManager.activeNotifications
                    for (notification in notifications) {
                        if (notification.id == 0) {
                            notifFound = true
                            break
                        }
                    }

                    if (!notifFound) {
                        break
                    }

                    if (Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(VibrationEffect.createOneShot(1200, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        vibrator.vibrate(1200)
                    }
                    pool.play(singleton.id, 1f, 1f, 1, 0, 1.0f)

                    Thread.sleep(1500)
                }
            }
        }

        internal fun loudest(context: Context, stream: Int) {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val loudest = manager.getStreamMaxVolume(stream)
            manager.setStreamVolume(stream, loudest, 0)
        }

        @RequiresApi(Build.VERSION_CODES.M)
        internal fun alert(context: Context, siren: Boolean = false) {
            val url = URL("http://192.168.19.177:9090/reportfall")

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; utf-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true

                fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

                var location = if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                } else {
                    val locationTask = fusedLocationClient.lastLocation

                    locationTask.addOnSuccessListener { location ->
                        if (location != null) {
                            val sharedPreferences = context.getSharedPreferences("altermarkive.guardian_preferences", Context.MODE_PRIVATE)
                            val name = sharedPreferences.getString("name", "Unknown") ?: "Unknown"
                            val jsonInputString = "{\"lat\": ${location.latitude}, \"lng\": ${location.longitude}, \"name\": \"${name}\"}"
                            outputStream.use { os ->
                                val input = jsonInputString.toByteArray(charset("utf-8"))
                                os.write(input, 0, input.size)
                            }
                        }
                    }
                }

            }

            if (siren) {
                siren(context)
            }
        }
    }
}