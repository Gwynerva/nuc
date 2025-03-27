package ru.gwynerva.nuc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent interceptorService = new Intent(context, InterceptCallService.class);
            context.startService(interceptorService);
        }
    }
}