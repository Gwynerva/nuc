package ru.gwynerva.nuc;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

public class PermissionManager {

    // Check if all required permissions are granted
    public static boolean areAllPermissionsGranted(Context context) {
        return isCallScreeningRoleHeld(context) &&
               isContactsPermissionGranted(context) &&
               isNotificationsPermissionGranted(context);
    }

    // Check if call screening role is held
    public static boolean isCallScreeningRoleHeld(Context context) {
        RoleManager roleManager = (RoleManager) context.getSystemService(Context.ROLE_SERVICE);
        return roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    // Check if contacts permission is granted
    public static boolean isContactsPermissionGranted(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
               == PackageManager.PERMISSION_GRANTED;
    }

    // Check if notification permission is granted
    public static boolean isNotificationsPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                   == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Always true for API < 33
    }
}
