package ru.gwynerva.nuc;

import static android.telecom.Call.Details.DIRECTION_INCOMING;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

public class InterceptCallService extends CallScreeningService {
    public enum State { ACTIVE, PAUSED, STOPPED }
    private State currentState = State.STOPPED;
    private long pauseUntilTimestamp = 0; // timestamp until when service should be paused


    private final String NOTIFICATION_CHANNEL_ID = "nuc";
    private final String TAG = "NUC Call Interceptor";
    private final int NOTIFICATION_ID = 1;

    // Constants for Intent communication
    public static final String ACTION_SET_STATE = "ru.gwynerva.nuc.SET_STATE";
    public static final String ACTION_SET_ALLOW_RECALL = "ru.gwynerva.nuc.SET_ALLOW_RECALL";

    private final int SELF_UPDATE_INTERVAL = 5000;
    private final Handler selfUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable selfUpdaterRunnable;

    private boolean allowRecall = false;
    private final Map<String, Long> recallMap = new HashMap<>();

    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();

        preferences = getSharedPreferences(Preferences.NAME, MODE_PRIVATE);
        currentState = State.valueOf(preferences.getString(Preferences.KEY_STATE, State.STOPPED.toString()));
        allowRecall = preferences.getBoolean(Preferences.KEY_ALLOW_RECALL, true);

        checkPauseStateExpiration();
        createNotificationChannel();
        createServiceNotification();
        updateServiceNotification();
        startSelfUpdater();

        Log.i(TAG, "__CREATED__");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_SET_STATE:
                    setState(State.valueOf(intent.getStringExtra("state")));
                    break;
                case ACTION_SET_ALLOW_RECALL:
                    setAllowRecall(intent.getBooleanExtra("allow", true));
                    break;
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopSelfUpdater();
        Log.i(TAG, "__DESTROYED__");
        super.onDestroy();
    }

    private void clearPauseState() {
        pauseUntilTimestamp = 0;
        preferences.edit().putLong(Preferences.KEY_PAUSE_UNTIL, 0).apply();
    }

    private void checkPauseStateExpiration() {
        pauseUntilTimestamp = preferences.getLong(Preferences.KEY_PAUSE_UNTIL, 0);

        if (currentState == State.PAUSED && pauseUntilTimestamp > 0 && System.currentTimeMillis() >= pauseUntilTimestamp) {
            pauseUntilTimestamp = 0;
            preferences.edit().putLong(Preferences.KEY_PAUSE_UNTIL, 0).apply();
            currentState = State.ACTIVE;
            preferences.edit().putString(Preferences.KEY_STATE, currentState.toString()).apply();
            updateServiceNotification();
        }
    }

    //
    // Actions
    //

    public void setState(State newState) {
        if (newState != currentState) {
            Log.i(TAG, "State changed from " + currentState + " to " + newState);

            currentState = newState;
            preferences.edit().putString(Preferences.KEY_STATE, newState.toString()).apply();

            switch (newState) {
                case ACTIVE:
                case STOPPED:
                    clearPauseState();
                    break;
            }

            updateServiceNotification();
        }
    }

    public void setAllowRecall(boolean allow) {
        if (!allow) {
            cleanupRecallMap();
        }

        allowRecall = allow;
        preferences.edit().putBoolean(Preferences.KEY_ALLOW_RECALL, allowRecall).apply();
    }

    //
    // Call Handling
    //

    @Override
    public void onScreenCall(@NonNull Call.Details details) {
        Log.i(TAG, "Call caught!");

        if (currentState != State.ACTIVE) {
            allowCall(details, "Protection is not active.");
            return;
        }

        if (details.getCallDirection() != DIRECTION_INCOMING) {
            allowCall(details, "Not an incoming call.");
            return;
        }

        String callNumber = "";
        if (details.getHandle() != null) {
            callNumber = details.getHandle().getSchemeSpecificPart();
        }

        if (callNumber.isEmpty()) {
            allowCall(details, "Empty call number.");
            return;
        }

        if (!PermissionManager.isContactsPermissionGranted(this)) {
            allowCall(details, "No contacts permission. Cannot check if caller is in contacts.");
            return;
        }

        boolean isContact;
        Uri lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(callNumber));
        try (Cursor cursor = getContentResolver().query(
                lookupUri,
                new String[]{ContactsContract.PhoneLookup._ID},
                null, null, null)) {
            isContact = (cursor != null && cursor.getCount() > 0);
        } catch (Exception e) {
            allowCall(details, "Failed to check call number in contacts.");
            return;
        }

        if (isContact) {
            allowCall(details, "It is a contact call.");
            return;
        }

        if (allowRecall) {
            cleanupRecallMap();

            if (recallMap.containsKey(callNumber)) {
                allowCall(details, "Repeated call within recall period.");
                return;
            }

            long now = System.currentTimeMillis();
            recallMap.put(callNumber, now + 5 * 60 * 1000);
            Log.i(TAG, "Unknown call number stored for 5 minutes!");
        }

        int rejectedCallsNumber = preferences.getInt(Preferences.KEY_REJECTED_CALLS, 0);
        rejectedCallsNumber++;
        preferences.edit().putInt(Preferences.KEY_REJECTED_CALLS, rejectedCallsNumber).apply();

        Log.i(TAG, "Rejecting unknown call.");
        CallResponse.Builder responseBuilder = new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true);
        respondToCall(details, responseBuilder.build());
    }

    private void cleanupRecallMap() {
        long now = System.currentTimeMillis();
        recallMap.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private void allowCall(@NonNull Call.Details details, String message) {
        Log.i(TAG, message + " Allow.");
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build();
        respondToCall(details, response);
    }

    //
    // Notification
    //

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Call Screening Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setShowBadge(false);  // Disable badges for this channel
        channel.setSound(null, null);
        channel.setDescription("Shows the status of call protection service");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
    }

    // Creates a notification based on the current state.
    private Notification createNotification() {
        String contentTitle;
        String contentText = null;

        switch (currentState) {
            case ACTIVE:
                contentTitle = getString(R.string.protection_active);
                break;
            case PAUSED:
            default:
                contentTitle = getString(R.string.protection_paused);
                contentText = getString(R.string.on_pause_until, TimeUtils.formatTimestamp(pauseUntilTimestamp, getString(R.string.tomorrow)));
                break;
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, pendingIntentFlags);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSmallIcon(currentState == State.ACTIVE ?
                    R.drawable.protection_active : R.drawable.protection_pause)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setNumber(0)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .setWhen(System.currentTimeMillis())
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void createServiceNotification() {
        if (currentState != State.STOPPED) {
            startForeground(NOTIFICATION_ID, createNotification());
        }
    }

    private void updateServiceNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            if (currentState == State.STOPPED) {
                notificationManager.cancel(NOTIFICATION_ID);
                stopForeground(true);
            } else {
                notificationManager.notify(NOTIFICATION_ID, createNotification());
            }
        }
    }

    //
    // Self Updater
    //

    private void startSelfUpdater() {
        selfUpdaterRunnable = new Runnable() {
            @Override
            public void run() {
                checkPauseStateExpiration();
                //updateServiceNotification();
                selfUpdateHandler.postDelayed(this, SELF_UPDATE_INTERVAL);
            }
        };
        selfUpdateHandler.postDelayed(selfUpdaterRunnable, SELF_UPDATE_INTERVAL);
    }

    private void stopSelfUpdater() {
        if (selfUpdaterRunnable != null) {
            selfUpdateHandler.removeCallbacks(selfUpdaterRunnable);
            selfUpdaterRunnable = null;
        }
    }
}