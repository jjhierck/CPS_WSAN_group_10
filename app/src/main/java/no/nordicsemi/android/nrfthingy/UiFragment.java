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

package no.nordicsemi.android.nrfthingy;

import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import no.nordicsemi.android.nrfthingy.common.ExtendedBluetoothDevice;
import no.nordicsemi.android.nrfthingy.common.MultipleDeviceListAdapter;
import no.nordicsemi.android.nrfthingy.common.MultipleScannerFragment;
import no.nordicsemi.android.nrfthingy.common.ScannerFragmentListener;
import no.nordicsemi.android.nrfthingy.common.Utils;
import no.nordicsemi.android.nrfthingy.database.DatabaseContract;
import no.nordicsemi.android.nrfthingy.database.DatabaseHelper;
import no.nordicsemi.android.nrfthingy.thingy.Thingy;
import no.nordicsemi.android.thingylib.ThingySdkManager;
import no.nordicsemi.android.thingylib.utils.ThingyUtils;

import static no.nordicsemi.android.nrfthingy.common.Utils.INITIAL_CONFIG_STATE;
import static no.nordicsemi.android.nrfthingy.common.Utils.PREFS_INITIAL_SETUP;
import static no.nordicsemi.android.nrfthingy.common.Utils.isAppInitialisedBefore;

public class UiFragment extends Fragment implements ScannerFragmentListener {
    // NOTE: The old contents of this class can be found in OldUiFragment.java

    Button refreshConnectedDevicesBtn;
    Button disconnectAllDevicesBtn;
    Button connectRestartBtn;
    Button flashAllThingiesBtn;

    TextView topText;
    ListView devicesList;

    private ImageView mHeaderToggle;

    private MultipleDeviceListAdapter mAdapter;
    private BluetoothDevice mSelectedDevice;
    private ThingySdkManager mThingySdkManager;
    private MultipleScannerFragment mScannerFragment;

    private DatabaseHelper mDatabaseHelper;

    public static UiFragment newInstance(final BluetoothDevice device) {
        UiFragment fragment = new UiFragment();
        final Bundle args = new Bundle();
        args.putParcelable(Utils.CURRENT_DEVICE, device);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mSelectedDevice = getArguments().getParcelable(Utils.CURRENT_DEVICE);
        }
        mScannerFragment = MultipleScannerFragment.getInstance(ThingyUtils.THINGY_BASE_UUID, this);
        mThingySdkManager = ThingySdkManager.getInstance();
        mDatabaseHelper = new DatabaseHelper(requireContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_ui, container, false);

        refreshConnectedDevicesBtn = rootView.findViewById(R.id.refresh_connected_devices);
        disconnectAllDevicesBtn = rootView.findViewById(R.id.disconnect_all);
        connectRestartBtn = rootView.findViewById(R.id.restart_connection);
        flashAllThingiesBtn = rootView.findViewById(R.id.flash_all_red);

        topText = rootView.findViewById(R.id.current_device);
        devicesList = rootView.findViewById(R.id.connected_devices_list);
        devicesList.setEmptyView(rootView.findViewById(android.R.id.empty));
        devicesList.setAdapter(mAdapter = new MultipleDeviceListAdapter());

//        List<BluetoothDevice> devices = mThingySdkManager.getConnectedDevices();
//        if (devices.size() > 0) {
//            topText.setText("");
//            for (final BluetoothDevice device : devices) {
//                topText.setText(String.format("%s - %s", topText.getText(), device.getName()));
//            }
//        }

        refreshConnectedDevicesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });

        flashAllThingiesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flashAllThingies();
            }
        });

        disconnectAllDevicesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectAllDevices();
            }
        });

        connectRestartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartConnection();
            }
        });

        return rootView;
    }

    /**
     * Adapted from InitialConfigurationActivity.getStarted()
     */
    public void addToDatabase(BluetoothDevice device, String name) {
        if (!isAppInitialisedBefore(requireContext())) {
            final SharedPreferences sp = requireContext().getSharedPreferences(PREFS_INITIAL_SETUP, requireActivity().MODE_PRIVATE);
            sp.edit().putBoolean(INITIAL_CONFIG_STATE, true).apply();
        }

        final String address = device.getAddress();

        if (!mDatabaseHelper.isExist(address)) {
            mDatabaseHelper.insertDevice(address, name); // device.getName());
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_TEMPERATURE);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_PRESSURE);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_HUMIDITY);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_AIR_QUALITY);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_COLOR);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_BUTTON);
            mDatabaseHelper.updateNotificationsState(address, true, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_QUATERNION);
            mThingySdkManager.setSelectedDevice(device);
        }
        updateSelectionInDb(new Thingy(device), false);
    }

    /**
     * Copied from InitialConfigurationActivity
     */
    private void updateSelectionInDb(final no.nordicsemi.android.nrfthingy.thingy.Thingy thingy, final boolean selected) {
        final ArrayList<Thingy> thingyList = mDatabaseHelper.getSavedDevices();
        for (int i = 0; i < thingyList.size(); i++) {
            if (thingy.getDeviceAddress().equals(thingyList.get(i).getDeviceAddress())) {
                mDatabaseHelper.setLastSelected(thingy.getDeviceAddress(), selected);
            } else {
                mDatabaseHelper.setLastSelected(thingyList.get(0).getDeviceAddress(), !selected);
            }
        }
    }

    public void refresh() {
        mAdapter.clearDevices();
        //List<BluetoothDevice> devices = mThingySdkManager.getConnectedDevices();
        List<ExtendedBluetoothDevice> scannerDevices;
        scannerDevices = mScannerFragment.connectedDevices;

        List<ExtendedBluetoothDevice> actuallyConnectedDevices;
        actuallyConnectedDevices = new ArrayList<>();

        if (scannerDevices != null) {
            for (final ExtendedBluetoothDevice scannerDevice : scannerDevices) {
                addToDatabase(scannerDevice.device, scannerDevice.device.getName());
                for (final BluetoothDevice connectedDevice : mThingySdkManager.getConnectedDevices()) {
                    if (connectedDevice.getAddress().equals(scannerDevice.device.getAddress())) {
                        actuallyConnectedDevices.add(scannerDevice);
                        break;
                    }
                }
            }
        }
        mAdapter.updateDevices(actuallyConnectedDevices);
        topText.setText(String.format("%d devices connected", mThingySdkManager.getConnectedDevices().size()));
    }

    public void flashAllThingies() {
        for (final BluetoothDevice device : mThingySdkManager.getConnectedDevices()) {
            //flashLed(device, ThingyUtils.LED_PURPLE);
            breatheLed(device, ThingyUtils.LED_RED, 200);

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    breatheLed(device, ThingyUtils.LED_GREEN);
                }
            }, 5000);   //5 seconds
        }
    }

    public void restartConnection() {
        mAdapter.clearDevices();
        mScannerFragment.connectedDevices = new ArrayList<>();
        mThingySdkManager.disconnectFromAllThingies();
        mScannerFragment.show(getActivity().getSupportFragmentManager(), null); // Show the scanner fragment
    }

    public void disconnectAllDevices() {
        mAdapter.clearDevices();
        mScannerFragment.connectedDevices = new ArrayList<>();
        mThingySdkManager.disconnectFromAllThingies();
    }

    private void breatheLed(final BluetoothDevice device, final int colorIndex, int breatheInterval) {
        final Thingy thingy = mDatabaseHelper.getSavedDevice(device.getAddress());
        if (mThingySdkManager.isConnected(device)) {
            mThingySdkManager.setBreatheLedMode(device, colorIndex, ThingyUtils.DEFAULT_LED_INTENSITY, breatheInterval);
        } else {
            Utils.showToast(getActivity(), "Please configureThingy to " + thingy.getDeviceName() + " before you proceed!");
        }
    }

    private void breatheLed(final BluetoothDevice device, final int colorIndex) {
        breatheLed(device, colorIndex, ThingyUtils.DEFAULT_BREATHE_INTERVAL);
    }


    private void flashLed(final BluetoothDevice device, final int colorIndex) {
        final Thingy thingy = mDatabaseHelper.getSavedDevice(device.getAddress());
        if (mThingySdkManager.isConnected(device)) {
            mThingySdkManager.setOneShotLedMode(device, colorIndex, ThingyUtils.DEFAULT_LED_INTENSITY);
        } else {
            Utils.showToast(getActivity(), "Please configureThingy to " + thingy.getDeviceName() + " before you proceed!");
        }
    }

    @Override
    public void onDeviceSelected(BluetoothDevice device, String name) {

    }

    @Override
    public void onNothingSelected() {

    }

}