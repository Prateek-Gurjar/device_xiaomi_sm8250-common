/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.charge;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;
import org.lineageos.settings.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BypassChargeTileService extends TileService {

    private static final String TAG = "BypassChargeTile";
    private ChargeUtils chargeUtils;
    private ExecutorService executorService;
    private Handler mainHandler;
    private final AtomicBoolean operationInProgress = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        chargeUtils = new ChargeUtils(this);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) executorService.shutdown();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        refreshTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!operationInProgress.compareAndSet(false, true)) return;

        executorService.execute(() -> {
            Boolean enabled = chargeUtils.getBypassChargeState();
            if (!chargeUtils.isBypassChargeSupported() || enabled == null) {
                mainHandler.post(() -> {
                    updateTileUI(null);
                    finishOperation();
                });
                return;
            }

            if (enabled) {
                boolean success = chargeUtils.enableBypassCharge(false);
                mainHandler.post(() -> {
                    if (!success) showToast(R.string.charge_bypass_write_failed);
                    refreshTile();
                    finishOperation();
                });
                return;
            }

            ChargeUtils.SafetyCheckResult safetyCheck = chargeUtils.performSafetyChecks();
            mainHandler.post(() -> {
                if (!safetyCheck.isSafe()) {
                    showToast(getString(R.string.charge_bypass_safety_failed,
                            safetyCheck.getReason()));
                    refreshTile();
                    finishOperation();
                    return;
                }
                showDialog(new AlertDialog.Builder(this)
                        .setTitle(R.string.charge_bypass_title)
                        .setMessage(R.string.charge_bypass_warning)
                        .setPositiveButton(android.R.string.ok, (dialog, which) ->
                                executorService.execute(() -> {
                                    boolean success = chargeUtils.enableBypassCharge(true);
                                    mainHandler.post(() -> {
                                        if (!success) showToast(R.string.charge_bypass_write_failed);
                                        refreshTile();
                                        finishOperation();
                                    });
                                }))
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            refreshTile();
                            finishOperation();
                        })
                        .create());
            });
        });
    }

    private void refreshTile() {
        if (executorService == null || executorService.isShutdown()) return;
        executorService.execute(() -> {
            Boolean enabled = chargeUtils.isBypassChargeSupported()
                    ? chargeUtils.getBypassChargeState() : null;
            mainHandler.post(() -> updateTileUI(enabled));
        });
    }

    private void finishOperation() {
        operationInProgress.set(false);
    }

    private void updateTileUI(Boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) return;
        if (enabled == null) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }

    private void showToast(int messageId) {
        Toast.makeText(this, messageId, Toast.LENGTH_LONG).show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public static void updateTile(Context context) {
        try {
            requestListeningState(context,
                    new ComponentName(context, BypassChargeTileService.class));
        } catch (Exception e) {
            Log.e(TAG, "Failed to request tile update", e);
        }
    }
}
