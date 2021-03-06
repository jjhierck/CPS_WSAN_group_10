/*
 * Copyright (c) 2010 - 2017, Nordic Semiconductor ASA
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form, except as embedded into a Nordic
 *    Semiconductor ASA integrated circuit in a product or a software update for
 *    such product, must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. Neither the name of Nordic Semiconductor ASA nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * 4. This software, with or without modification, must only be used with a
 *    Nordic Semiconductor ASA integrated circuit.
 *
 * 5. Any software provided in binary form under this license must not be reverse
 *    engineered, decompiled, modified and/or disassembled.
 *
 * THIS SOFTWARE IS PROVIDED BY NORDIC SEMICONDUCTOR ASA "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY, NONINFRINGEMENT, AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NORDIC SEMICONDUCTOR ASA OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package no.nordicsemi.android.nrfthingy.common;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import no.nordicsemi.android.nrfthingy.MainActivity;
import no.nordicsemi.android.nrfthingy.R;
import no.nordicsemi.android.nrfthingy.UiFragment;
import no.nordicsemi.android.nrfthingy.thingy.Thingy;
import no.nordicsemi.android.nrfthingy.thingy.ThingyService;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;
import no.nordicsemi.android.thingylib.ThingySdkManager;
import no.nordicsemi.android.thingylib.utils.ThingyUtils;

/**
 * ScannerFragment class scan required BLE devices and shows them in a list. This class scans and filter devices with given BLE Service UUID which may be null. It contains a
 * list and a button to scan/cancel. The scanning will continue for 5 seconds and then stop.
 */
public class MultipleScannerFragment extends DialogFragment {
    private final static String TAG = "ScannerFragment";

    private final static String PARAM_UUID = "param_uuid";
    private final static String PARAM_UUID1 = "param_uuid1";
    private final static long SCAN_DURATION = 8000;  // Scan for 8 seconds
    /* package */static final int NO_RSSI = -1000;

    private final static int REQUEST_PERMISSION_REQ_CODE = 76; // any 8-bit number
    private static final int MAX_CONNECTED_THINGIES = 4;

    private LinearLayout troubleshootView;
    private MultipleDeviceListAdapter mAdapter;
    private Handler mHandler = new Handler();
    private Button mScanButton;

    private UiFragment returnFragment;

    private ParcelUuid mUuid;
    private boolean mIsScanning = false;

    private ThingySdkManager mThingySdkManager;

    private Map<String, ScanResult> mResultsMap;

    public ArrayList<ExtendedBluetoothDevice> connectedDevices;

    /**
     * Static implementation of fragment so that it keeps data when phone orientation is changed For standard BLE Service UUID, we can filter devices using normal android provided command
     * startScanLe() with required BLE Service UUID For custom BLE Service UUID, we will use class ScannerServiceParser to filter out required device
     */
    public static MultipleScannerFragment getInstance(@Nullable final UUID uuid, UiFragment returnFragment) {
        final MultipleScannerFragment fragment = new MultipleScannerFragment();

        final Bundle args = new Bundle();
        if (uuid != null) {
            args.putParcelable(PARAM_UUID, new ParcelUuid(uuid));
        }
        fragment.setArguments(args);
        fragment.returnFragment = returnFragment;

        return fragment;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle args = getArguments();
//        mUuid = args.getParcelable(PARAM_UUID);
        mUuid = new ParcelUuid(ThingyUtils.THINGY_BASE_UUID);
        mThingySdkManager = ThingySdkManager.getInstance();
        mResultsMap = new HashMap<>();
        connectedDevices = new ArrayList<>();
    }

    @Override
    public void onStop() {
        // Stop scan moved from onDestroyView to onStop
        stopScan();
        super.onStop();
    }

    /**
     * When dialog is created then set AlertDialog with list and button views
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        @SuppressLint("InflateParams") final View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_scanner_device_selection, null);
        final ListView listview = dialogView.findViewById(android.R.id.list);

        troubleshootView = dialogView.findViewById(R.id.troubleshoot_guide);

        listview.setEmptyView(dialogView.findViewById(android.R.id.empty));
        listview.setAdapter(mAdapter = new MultipleDeviceListAdapter());
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                stopScan();
                dismiss();

                final ScannerFragmentListener listener = (ScannerFragmentListener) returnFragment;
                final ExtendedBluetoothDevice device = (ExtendedBluetoothDevice) mAdapter.getItem(position);
                listener.onDeviceSelected(device.device, device.name != null ? device.name : getString(R.string.not_available));
            }
        });

        final AlertDialog dialog = builder
                .setTitle(R.string.scanner_list)
                .setView(dialogView)
                .setPositiveButton(R.string.scanner_action_scan, null)
                .show();

        // Button listener needs to be set like this, otherwise it would always dismiss the dialog.
        mScanButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mIsScanning) {
                    final ScannerFragmentListener listener = (ScannerFragmentListener) returnFragment;
                    listener.onNothingSelected();
                    dialog.cancel();
                } else {
                    startScan();
                }
            }
        });

        if (savedInstanceState == null)
            startScan();
        return dialog;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final @NonNull String[] permissions, final @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_REQ_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // We have been granted the Manifest.permission.ACCESS_FINE_LOCATION permission. Now we may proceed with scanning.
                startScan();
            } else {
                Toast.makeText(getActivity(), R.string.rationale_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Scan for 5 seconds and then stop scanning when a BluetoothLE device is found then mLEScanCallback is activated This will perform regular scan for custom BLE Service UUID and then filter out
     * using class ScannerServiceParser
     */
    private void startScan() {
        // Since Android 6.0 we need to obtain either Manifest.permission.ACCESS_FINE_LOCATION or Manifest.permission.ACCESS_FINE_LOCATION to be able to scan for
        // Bluetooth LE devices. This is related to beacons as proximity devices.
        // On API older than Marshmallow the following code does nothing.
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // When user pressed Deny and still wants to use this functionality, show the rationale
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                return;
            }

            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_REQ_CODE);
            return;
        }

        mAdapter.clearDevices();
        mScanButton.setText(R.string.scanner_action_stop);
        troubleshootView.setVisibility(View.VISIBLE);

        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(750).setUseHardwareBatchingIfSupported(false).setUseHardwareFilteringIfSupported(false).build();
        final List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(mUuid).build());
        scanner.startScan(filters, settings, scanCallback);

        mIsScanning = true;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mIsScanning) {
                    stopScan();
                    connectDevices();
                    dismiss();  // Stop the scanning activity window
                }
            }
        }, SCAN_DURATION);
    }

    /**
     * Stop scan if user tap Cancel button
     */
    private void stopScan() {
        if (mIsScanning) {
            mScanButton.setText(R.string.scanner_action_scan);

            final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            scanner.stopScan(scanCallback);

            mIsScanning = false;
        }
    }

    private void connectDevices() {
        List<ScanResult> resultList = new ArrayList<ScanResult>(mResultsMap.values());

        // Create a comparator to sort the array list based on the rssi values (high to low)
        Comparator<ExtendedBluetoothDevice> compareByRssi = new Comparator<ExtendedBluetoothDevice>() {
            @Override
            public int compare(ExtendedBluetoothDevice o1, ExtendedBluetoothDevice o2) {
                return Integer.compare(o2.rssi, o1.rssi);
            }
        };

        // Populate a list with devices to be sorted later
        ArrayList<ExtendedBluetoothDevice> sortedDevices = new ArrayList<>();
        for (final ScanResult result : resultList) {
            Log.w("scanCallback", String.format("Unsorted: %s (rssi: %d)", result.getDevice().getName(), result.getRssi()));
            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();  // Signal strength
            sortedDevices.add(new ExtendedBluetoothDevice(device, rssi));
        }
        Collections.sort(sortedDevices, compareByRssi); // Sort the list by RSSI

        int nrConnectedDevices = mThingySdkManager.getConnectedDevices().size();  // See how many devices are currently connected
        int i = 0; // Results counter
        for (final ExtendedBluetoothDevice extDevice : sortedDevices) {
            if (i + nrConnectedDevices < this.MAX_CONNECTED_THINGIES) { // Only connect up to 4 devices with the strongest RSSI
                BluetoothDevice device = extDevice.device;
                String address = device.getAddress();

                mThingySdkManager.connectToThingy(requireContext(), device, ThingyService.class);
                connectedDevices.add(extDevice);
                Log.w("scanCallback", String.format("Connecting to %s (rssi: %d)", device.getName(), extDevice.rssi));
                i++; // increment the results counter
                Toast toast = Toast.makeText(requireContext(), String.format("Connecting to %s (rssi: %d)", device.getName(), extDevice.rssi), Toast.LENGTH_SHORT);
                toast.show();
            }
        }
        //connectedDevices = sortedDevices;
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, @NonNull final ScanResult result) {
            // do nothing
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onBatchScanResults(final List<ScanResult> results) {
            if (results.size() > 0 && troubleshootView.getVisibility() == View.VISIBLE) {
                troubleshootView.setVisibility(View.GONE);
            }
            for (final ScanResult result : results) {
                mResultsMap.put(result.getDevice().getAddress(), result);
            }

            mAdapter.update(results);
        }

        @Override
        public void onScanFailed(final int errorCode) {
            // should never be called
        }
    };
}
