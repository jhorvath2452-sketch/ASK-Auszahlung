package at.mannersdorf.ask.auszahlung

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val BENACHRICHTIGUNGSKANAL_ID = "neue_bestaetigungen"
const val PUSH_THEMA = "neue_bestaetigungen"

/**
 * Empfängt Push-Benachrichtigungen (Firebase Cloud Messaging) und zeigt sie an -
 * auch wenn die App gerade offen ist (FCM zeigt "notification"-Nachrichten sonst
 * nur an, wenn die App im Hintergrund/geschlossen ist). Ausgelöst wird das von
 * der Cloud Function "sheetsProxy"-Nachbarfunktion, sobald eine neue Bestätigung
 * in Firestore gespeichert wird (siehe /functions).
 */
class PushNachrichtenDienst : FirebaseMessagingService() {

    override fun onMessageReceived(nachricht: RemoteMessage) {
        val titel = nachricht.notification?.title ?: "ASK Auszahlung"
        val text = nachricht.notification?.body ?: "Neue Bestätigung gespeichert."
        zeigeBenachrichtigung(this, titel, text)
    }

    // Ein neues Geräte-Token braucht die App nicht extra zu speichern, da alle
    // Geräte einfach das gemeinsame Thema "neue_bestaetigungen" abonnieren
    // (siehe MainActivity) statt einzeln adressiert zu werden.
    override fun onNewToken(token: String) {}
}

fun erstelleBenachrichtigungskanal(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val kanal = NotificationChannel(
            BENACHRICHTIGUNGSKANAL_ID,
            "Neue Bestätigungen",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Benachrichtigt, sobald eine neue Auszahlungsbestätigung gespeichert wurde."
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(kanal)
    }
}

fun zeigeBenachrichtigung(context: Context, titel: String, text: String) {
    val oeffneIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }
    val pendingIntent = PendingIntent.getActivity(context, 0, oeffneIntent, pendingIntentFlags)

    val benachrichtigung = NotificationCompat.Builder(context, BENACHRICHTIGUNGSKANAL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(titel)
        .setContentText(text)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(System.currentTimeMillis().toInt(), benachrichtigung)
}
