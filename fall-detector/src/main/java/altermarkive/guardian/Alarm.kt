package altermarkive.guardian

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient


class Alarm private constructor(val context: Guardian) {
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
    }

    companion object {
        private var cancel: Boolean = false
        private var singleton: Alarm? = null
        private lateinit var fusedLocationClient: FusedLocationProviderClient

        private const val VOLUME = 1f

        internal fun instance(context: Guardian): Alarm {
            var singleton = this.singleton
            if (singleton == null) {
                singleton = Alarm(context)
                this.singleton = singleton
            }
            return singleton
        }

        private fun createNotificationChannel(context: Context): String {
            val channelId = "fall-detector-alarm"
            val channelName = "$channelId Alarm"
            val channel = NotificationChannel(
                channelId,
                channelName, NotificationManager.IMPORTANCE_HIGH
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            val service = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(channel)
            return channelId
        }

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
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )

                val fullScreenIntent = Intent(context, Main::class.java)
                fullScreenIntent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                val fullScreenPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )

                val builder =
                    NotificationCompat.Builder(context, createNotificationChannel(context))
                builder
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("FALL DETECTED")
                    .setContentText("Fall Detected!!! Clear the notification to stop the alarm.")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setSilent(false)

                with(NotificationManagerCompat.from(context)) {
                    notify(0, builder.build())
                }

                val mNotificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            1200,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )

                    pool.play(singleton.id, VOLUME, VOLUME, 1, 0, 1.0f)

                    Thread.sleep(1500)
                }
            }
        }

        internal fun loudest(context: Context, stream: Int) {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val loudest = manager.getStreamMaxVolume(stream)
            manager.setStreamVolume(stream, loudest, 0)
        }

        internal fun alert(context: Context, siren: Boolean = false) {
            val intent = Intent().apply {
                action = "CANCEL"
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

            val fullScreenIntent = Intent(context, Main::class.java)
            fullScreenIntent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder =
                NotificationCompat.Builder(context, createNotificationChannel(context))
            builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("FALL DETECTED")
                .setContentText("Fall Detected!!! Clear the notification within 10 seconds to cancel the alarm. 10")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setSilent(true)

            with(NotificationManagerCompat.from(context)) {
                notify(2, builder.build())
            }

            val mNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            var cancel = false

            object : CountDownTimer(10000, 1000) {
                var switch = false
                override fun onTick(millisUntilFinished: Long) {
                    if (switch) {
                        switch = false
                        val singleton = this@Companion.singleton

                        // Gradually increase the volume of the alarm
                        val volume = Helper.clamp(
                            10000 - millisUntilFinished.toDouble(),
                            0.0,
                            10000.0,
                            0.001,
                            VOLUME.toDouble()
                        ).toFloat()
                        singleton?.pool?.play(singleton.id, volume, volume, 1, 0, 1.0f)
                    } else {
                        switch = true
                    }

                    var notifFound = false

                    val notifications = mNotificationManager.activeNotifications
                    for (notification in notifications) {
                        if (notification.id == 2) {
                            notifFound = true

                            builder.setContentText("Fall Detected!!! Clear the notification within 10 seconds to cancel the alarm. ${millisUntilFinished / 1000}")
                            with(NotificationManagerCompat.from(context)) {
                                notify(2, builder.build())
                            }

                            break
                        }
                    }

                    if (!notifFound) {
                        cancel = true
                        cancel()
                    }
                }

                override fun onFinish() {
                    var notifFound = false
                    for (notification in mNotificationManager.activeNotifications) {
                        if (notification.id == 2) {
                            mNotificationManager.cancel(2)
                            notifFound = true
                            break
                        }
                    }

                    if (!notifFound) {
                        cancel = true
                        cancel()
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Toast.makeText(
                                context,
                                "Missing location permission. Cannot send fall report.",
                                Toast.LENGTH_SHORT
                            ).show()

                            ActivityCompat.requestPermissions(
                                context as Main,
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                                1
                            )
                            return@launch
                        } else {
                            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    val sharedPreferences = context.getSharedPreferences(
                                        "altermarkive.guardian_preferences",
                                        Context.MODE_PRIVATE
                                    )
                                    val name = sharedPreferences.getString("name", "Unknown") ?: "Unknown"

                                    val requestBody = """
                        {
                            "lat": ${location.latitude},
                            "lng": ${location.longitude},
                            "name": "${name}"
                        }
                    """.trimIndent()
                                    val client = OkHttpClient()
                                    val request = okhttp3.Request.Builder()
                                        .url("http://10.75.118.219:9090/reportfall")
                                        .post(okhttp3.RequestBody.create(null, requestBody))
                                        .build()
                                    val response = client.newCall(request).execute()
                                    val responseCode = response.code
                                    Log.d("Alarm", responseCode.toString())
                                    if (responseCode == 200) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Fall report sent", Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "Failed to send fall report",
                                                Toast.LENGTH_SHORT
                                            )
                                                .show()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    CoroutineScope(Dispatchers.Default).launch {
                        if (siren) {
                            siren(context)
                        }
                    }
                }
            }.start()
        }
    }
}