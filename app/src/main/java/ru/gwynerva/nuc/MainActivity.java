package ru.gwynerva.nuc;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yandex.mobile.ads.banner.BannerAdEventListener;
import com.yandex.mobile.ads.banner.BannerAdSize;
import com.yandex.mobile.ads.banner.BannerAdView;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;

import java.util.Calendar;

public class MainActivity extends ComponentActivity {
    private TextView serviceStatus;
    private TextView rejectedCounter;
    private ImageButton startButton;
    private ImageButton pauseButton;
    private ImageButton stopButton;
    private CheckBox allowRecallCheckbox;
    private View backgroundCircle;
    private TextView pausedUntil;

    // Add CircleAnimator for managing circle animations
    private CircleAnimator circleAnimator;

    // SharedPreferences
    private SharedPreferences preferences;

    // Handler for periodic UI updates
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    // Ads
    @Nullable
    private BannerAdView mBannerAd = null;
    private View adContainerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize SharedPreferences
        preferences = getSharedPreferences(Preferences.NAME, MODE_PRIVATE);

        initViews();
        setupServiceButtons();

        // Initialize the circle animator
        circleAnimator = new CircleAnimator(backgroundCircle);

        // Initialize ad container view
        adContainerView = findViewById(R.id.bottomAds);

        // Wait until the ad container view is laid out before loading the ad
        adContainerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        adContainerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        mBannerAd = loadBannerAd(getAdSize());
                    }
                }
        );

        // Start UI update handler
        startPeriodicUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Start service to show notification
        Intent serviceIntent = new Intent(this, InterceptCallService.class);
        startService(serviceIntent);

        // Check if ALL permissions are granted, if not - show PermissionsActivity
        if (!PermissionManager.areAllPermissionsGranted(this)) {
            setServiceState(InterceptCallService.State.STOPPED);
            Intent permissionsIntent = new Intent(this, PermissionsActivity.class);
            startActivity(permissionsIntent);
        }

        updateUI();
    }

    @Override
    protected void onDestroy() {
        stopPeriodicUpdates();
        super.onDestroy();
    }

    @NonNull
    private BannerAdSize getAdSize() {
        final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        // Calculate the width of the ad, taking into account the padding in the ad container
        int adWidthPixels = adContainerView.getWidth();
        if (adWidthPixels == 0) {
            // If the ad hasn't been laid out, default to the full screen width
            adWidthPixels = displayMetrics.widthPixels;
        }
        final int adWidth = Math.round(adWidthPixels / displayMetrics.density);

        return BannerAdSize.stickySize(this, adWidth);
    }

    @NonNull
    private BannerAdView loadBannerAd(@NonNull final BannerAdSize adSize) {
        final BannerAdView bannerAd = findViewById(R.id.bottomAds);
        bannerAd.setAdSize(adSize);
        bannerAd.setAdUnitId("R-M-14693051-1");
        bannerAd.setBannerAdEventListener(new BannerAdEventListener() {
            @Override
            public void onAdLoaded() {
                // If this callback occurs after the activity is destroyed, you
                // must call destroy and return or you may get a memory leak
                if (isDestroyed() && mBannerAd != null) {
                    mBannerAd.destroy();
                }
            }
            @Override
            public void onAdFailedToLoad(@NonNull final AdRequestError adRequestError) {}
            @Override
            public void onAdClicked() {}
            @Override
            public void onLeftApplication() {}
            @Override
            public void onReturnedToApplication() {}
            @Override
            public void onImpression(@Nullable ImpressionData impressionData) {}
        });
        final AdRequest adRequest = new AdRequest.Builder().build();
        bannerAd.loadAd(adRequest);
        return bannerAd;
    }

    // Initialize UI components
    private void initViews() {
        // UI components
        serviceStatus = findViewById(R.id.serviceStatus);
        rejectedCounter = findViewById(R.id.rejectedCounter);
        startButton = findViewById(R.id.startServiceButton);
        pauseButton = findViewById(R.id.pauseServiceButton);
        stopButton = findViewById(R.id.stopServiceButton);
        backgroundCircle = findViewById(R.id.backgroundCircle);
        pausedUntil = findViewById(R.id.pausedUntil);
        pausedUntil.setVisibility(View.GONE);

        ImageButton repeatedCallsExplainButton = findViewById(R.id.repeatedCallsExplainButton);
        repeatedCallsExplainButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle(getString(R.string.allow_repeated_calls));
            builder.setMessage(getString(R.string.repeated_calls_explain));
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
            builder.show();
        });

        // Initialize allowRecallCheckbox
        allowRecallCheckbox = findViewById(R.id.allowRecallCheckbox);
        allowRecallCheckbox.setChecked(preferences.getBoolean(Preferences.KEY_ALLOW_RECALL, true));
        allowRecallCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Preferences.KEY_ALLOW_RECALL, isChecked).apply();

            Intent intent = new Intent(this, InterceptCallService.class);
            intent.setAction(InterceptCallService.ACTION_SET_ALLOW_RECALL);
            intent.putExtra("allow", isChecked);
            startService(intent);
        });
    }

    // Set up start, pause, and stop buttons for the service
    private void setupServiceButtons() {
        startButton.setOnClickListener(v -> {
            setServiceState(InterceptCallService.State.ACTIVE);
            updateUI();
        });

        pauseButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle(getString(R.string.pause_protection));
            String[] options = {
                    getString(R.string.two_hours),
                    getString(R.string.six_hours),
                    getString(R.string.until_tomorrow),
                    //"15 sec",
            };
            builder.setItems(options, (dialog, which) -> {
                long pauseUntil = calculatePauseTimestamp(which);
                preferences.edit().putLong(Preferences.KEY_PAUSE_UNTIL, pauseUntil).apply();
                setServiceState(InterceptCallService.State.PAUSED);
                updateUI();
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });

        stopButton.setOnClickListener(v -> {
            setServiceState(InterceptCallService.State.STOPPED);
            updateUI();
        });
    }

    // Calculate timestamp for when to resume service based on selection
    private long calculatePauseTimestamp(int option) {
        Calendar cal = Calendar.getInstance();
        switch (option) {
            case 0: // 2 hours
                cal.add(Calendar.HOUR, 2);
                break;
            case 1: // 6 hours
                cal.add(Calendar.HOUR, 6);
                break;
            case 2: // Until tomorrow (0:00)
                cal.add(Calendar.DAY_OF_YEAR, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                break;
//            case 3:
//                cal.add(Calendar.SECOND, 15);
//                break;
        }
        return cal.getTimeInMillis();
    }

    // Update UI based on service state and preferences
    private void updateUI() {
        InterceptCallService.State state = getServiceState();
        int rejectedCalls = preferences.getInt(Preferences.KEY_REJECTED_CALLS, 0);
        long pauseUntil = preferences.getLong(Preferences.KEY_PAUSE_UNTIL, 0);

        // Check if pause time has passed and we need to auto-resume
        if (state == InterceptCallService.State.PAUSED &&
                pauseUntil > 0 && System.currentTimeMillis() >= pauseUntil) {
            // Resume service
            preferences.edit().putLong(Preferences.KEY_PAUSE_UNTIL, 0).apply();
            setServiceState(InterceptCallService.State.ACTIVE);
            state = InterceptCallService.State.ACTIVE;
            pauseUntil = 0;
        }

        // Update background circle based on service status with animation
        switch (state) {
            case ACTIVE:
                circleAnimator.animateToActive();
                serviceStatus.setText(getString(R.string.protection_active));
                serviceStatus.setTextColor(getColor(R.color.active));
                pausedUntil.setVisibility(View.GONE);
                break;
            case PAUSED:
                circleAnimator.animateToPaused();
                serviceStatus.setTextColor(getColor(R.color.paused));
                serviceStatus.setText(getString(R.string.protection_paused));
                pausedUntil.setText(getString(R.string.on_pause_until, TimeUtils.formatTimestamp(pauseUntil, getString(R.string.tomorrow))));
                pausedUntil.setVisibility(View.VISIBLE);
                break;
            case STOPPED:
            default:
                circleAnimator.animateToStopped();
                serviceStatus.setText(getString(R.string.protection_stopped));
                serviceStatus.setTextColor(getColor(R.color.stopped));
                pausedUntil.setVisibility(View.GONE);
                break;
        }

        // Update rejected calls counter
        rejectedCounter.setText(getString(R.string.calls_skipped, rejectedCalls));

        // Update button states - now requires ALL permissions
        boolean permissionsGranted = PermissionManager.areAllPermissionsGranted(this);
        startButton.setEnabled(permissionsGranted && state != InterceptCallService.State.ACTIVE);
        pauseButton.setEnabled(permissionsGranted && state == InterceptCallService.State.ACTIVE);
        stopButton.setEnabled(permissionsGranted && state != InterceptCallService.State.STOPPED);

        // Update checkbox state from preferences
        boolean allowRecall = preferences.getBoolean(Preferences.KEY_ALLOW_RECALL, true);
        if (allowRecallCheckbox.isChecked() != allowRecall) {
            allowRecallCheckbox.setChecked(allowRecall);
        }
    }

    // Get current service state from SharedPreferences
    private InterceptCallService.State getServiceState() {
        String stateStr = preferences.getString(Preferences.KEY_STATE, InterceptCallService.State.STOPPED.toString());
        try {
            return InterceptCallService.State.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            return InterceptCallService.State.STOPPED;
        }
    }

    // Set service state in SharedPreferences and notify running service
    private void setServiceState(InterceptCallService.State state) {
        preferences.edit().putString(Preferences.KEY_STATE, state.toString()).apply();

        Intent intent = new Intent(this, InterceptCallService.class);
        intent.setAction(InterceptCallService.ACTION_SET_STATE);
        intent.putExtra("state", state.toString());
        startService(intent);
    }

    // Start periodic UI updates
    private void startPeriodicUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                updateHandler.postDelayed(this, 1000);
            }
        };
        updateHandler.postDelayed(updateRunnable, 1000);
    }

    // Stop periodic UI updates
    private void stopPeriodicUpdates() {
        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
}