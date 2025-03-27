package ru.gwynerva.nuc;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

public class PermissionsActivity extends ComponentActivity {
    private ImageView iconCalls, iconContacts, iconNotifications;
    private Button buttonCalls, buttonContacts, buttonNotifications;

    private ActivityResultLauncher<Intent> callScreeningLauncher;
    private ActivityResultLauncher<String> contactPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        initViews();
        registerLaunchers();
        updatePermissionStatus();
    }

    private void initViews() {
        // Initialize ImageViews for permission icons
        iconCalls = findViewById(R.id.iconCalls);
        iconContacts = findViewById(R.id.iconContacts);
        iconNotifications = findViewById(R.id.iconNotifications);

        // Initialize Buttons for permissions
        buttonCalls = findViewById(R.id.buttonCalls);
        buttonContacts = findViewById(R.id.buttonContacts);
        buttonNotifications = findViewById(R.id.buttonNotifications);

        // Setup Button Click Listeners
        buttonCalls.setOnClickListener(v -> requestCallScreeningPermission());
        buttonContacts.setOnClickListener(v -> requestContactsPermission());
        buttonNotifications.setOnClickListener(v -> requestNotificationsPermission());
    }

    private void registerLaunchers() {
        // Register launcher for call screening role
        callScreeningLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    updatePermissionStatus();
                    checkAllPermissionsGranted();
                }
        );

        // Register launcher for contacts permission
        contactPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    updatePermissionStatus();
                    checkAllPermissionsGranted();
                }
        );

        // Register launcher for notifications permission
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    updatePermissionStatus();
                    checkAllPermissionsGranted();
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
        checkAllPermissionsGranted();
    }

    private void updatePermissionStatus() {
        // Update call screening icon
        boolean isCallScreeningGranted = PermissionManager.isCallScreeningRoleHeld(this);
        iconCalls.setColorFilter(ContextCompat.getColor(this,
                isCallScreeningGranted ? R.color.active : R.color.stopped));
        buttonCalls.setEnabled(!isCallScreeningGranted);

        // Update contacts icon
        boolean isContactsGranted = PermissionManager.isContactsPermissionGranted(this);
        iconContacts.setColorFilter(ContextCompat.getColor(this,
                isContactsGranted ? R.color.active : R.color.stopped));
        buttonContacts.setEnabled(!isContactsGranted);

        // Update notifications icon - ensure this is checked independently
        boolean isNotificationsGranted = PermissionManager.isNotificationsPermissionGranted(this);
        iconNotifications.setColorFilter(ContextCompat.getColor(this,
                isNotificationsGranted ? R.color.active : R.color.stopped));
        buttonNotifications.setEnabled(!isNotificationsGranted);
    }

    private void checkAllPermissionsGranted() {
        // Only finish activity if ALL permissions have been granted
        if (PermissionManager.areAllPermissionsGranted(this)) {
            finish();
        }
    }

    // Request call screening role
    private void requestCallScreeningPermission() {
        RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
            callScreeningLauncher.launch(intent);
        }
    }

    // Request contacts permission
    private void requestContactsPermission() {
        contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
    }

    // Request notification permission (for API 33+)
    private void requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            // For older Android versions, update permission status but don't mark as finished
            // unless all permissions are granted
            updatePermissionStatus();
            checkAllPermissionsGranted();
        }
    }
}
