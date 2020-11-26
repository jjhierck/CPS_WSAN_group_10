package no.nordicsemi.android.nrfthingy.ClusterHead;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClhScan {
    private BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothLeScanner mCLHscanner ;
    private final String LOG_TAG="CLH Scanner:";

    private Handler handler = new Handler();
    private Handler handler2 = new Handler();
    private boolean mScanning;
    private byte mClhID=1;
    private boolean mIsSink=false;
    private ScanSettings mScanSettings;

    private SparseArray<Integer> ClhScanHistoryArray=new SparseArray();

    //private static final int MAX_PROCESS_LIST_ITEM=128;
    //private ClhAdvertisedData clhAdvData=new ClhAdvertisedData();
    private ClhAdvertise mClhAdvertiser;
    private ArrayList<ClhAdvertisedData> mClhProcDataList ;
    private ClhProcessData mClhProcessData;
    private ArrayList<ClhAdvertisedData> mClhAdvDataList;
    private static final int MAX_ADVERTISE_LIST_ITEM=128;

    public ClhScan()
    {

    }

    public ClhScan(ClhAdvertise clhAdvObj,ClhProcessData clhProcDataObj)
    {//constructor, set 2 alias to Clh advertiser and processor
        mClhAdvertiser=clhAdvObj;
        mClhAdvDataList=mClhAdvertiser.getAdvertiseList();
        mClhProcessData=clhProcDataObj;
        mClhProcDataList=clhProcDataObj.getProcessDataList();
    }


    public int BLE_scan() {
        boolean result=true;
        byte[] advsettings=new byte[16];
        byte[] advData= new byte[256];
        int length;
        final List<ScanFilter> filters = new ArrayList<>();

        if (!mScanning) {
            //verify BLE available
            mCLHscanner = mAdapter.getBluetoothLeScanner();
            if (mCLHscanner == null) {
                Log.i(LOG_TAG, "BLE not supported");
                return ClhErrors.ERROR_CLH_BLE_NOT_ENABLE;
            }

            //setting
            ScanSettings ClhScanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    .build();

            //set filter: filter name
            ScanFilter filter = new ScanFilter.Builder()
                    .setDeviceName(ClhConst.clusterHeadName)
                    .build();
            filters.add(filter);
            Log.i(LOG_TAG, "filters"+ filters.toString());

            mScanSettings =ClhScanSettings;
// Stops scanning after 60 seconds.

            // Create a timer to stop scanning after a pre-defined scan period.
            //rest, then restart to avoid auto disable from Android
           handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mCLHscanner.stopScan(CLHScanCallback);
                    Log.i(LOG_TAG, "Stop scan");
                    //start another timer for resting in 1s
                    handler2.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mScanning = true;
                            mCLHscanner.startScan(filters, mScanSettings, CLHScanCallback);
                        }
                    },ClhConst.REST_PERIOD);
                }
            }, ClhConst.SCAN_PERIOD);

            mScanning = true;
            mCLHscanner.startScan(filters, ClhScanSettings, CLHScanCallback);
            Log.i(LOG_TAG, "Start scan");
        }
        else
        {
            return ClhErrors.ERROR_CLH_SCAN_ALREADY_START;
        }

        return ClhErrors.ERROR_CLH_NO;
    }

    public void stopScanCLH()
    {
        mScanning = false;
        mCLHscanner.stopScan(CLHScanCallback);
        Log.i(LOG_TAG, "Stop scan");
    }


    private ScanCallback CLHScanCallback = new ScanCallback() {
        @Override
        public final void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            //no need this code since already have name filter
            /*if( result == null
                    || result.getDevice() == null
                    || TextUtils.isEmpty(result.getDevice().getName()) ) {
                Log.i(LOG_TAG, "Empty name space");
                return;
                //if( result == null || result.getDevice() == null)  return;
            }*/

            //check RSSI to remove weak signal ones
            if (result.getRssi()<ClhConst.MIN_SCAN_RSSI_THRESHOLD) {
                Log.i(LOG_TAG,"low RSSI");
                return;
            }

            SparseArray<byte[]> manufacturerData = result.getScanRecord().getManufacturerSpecificData(); //get data
            processScanData(manufacturerData);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e( "BLE", "Discovery onScanFailed: " + errorCode );
            super.onScanFailed(errorCode);
        }
    };


/*process received data of BLE Manufacturer field
 include:
- Manufacturer Specification (in manufacturerData.key): "unique packet ID", include
            2 bytes: 0XAABB: AA: Source Cluster Head ID: 0-127
                            BB: Packet ID: 0-254 (unique for each packet)
 - Manufacturer Data (in manufacturerData.value): remained n data bytes (manufacturerData.size())
-------------------*/

    public void processScanData(SparseArray<byte[]> manufacturerData) {


        if(manufacturerData==null)
        {
            Log.i(LOG_TAG, "no Data");
            return;

        }
        int receiverID=manufacturerData.keyAt(0);

        //reflected data (received cluster head ID = device Clh ID) -> skip
        if(mClhID==(receiverID>>8))
        {
            Log.i(LOG_TAG,"reflected data, mClhID "+mClhID +", recv:" +(receiverID>>8) );
            return;
        }
        Log.i(LOG_TAG,"ID data "+ (receiverID>>8)+ "  "+(receiverID&0xFF) );

        /* check packet has been yet recieved by searching the "unique packet ID" history list
         - history list include:
                        Key: unique packet ID
                        life counter: time of the packet lived in history list
          --------------*/

        if (ClhScanHistoryArray.indexOfKey(manufacturerData.keyAt(0))<0)
        {//not yet received
            //history not yet full, update new "unique packet ID" to history list, reset life counter
            if(ClhScanHistoryArray.size()<ClhConst.SCAN_HISTORY_LIST_SIZE)
            {
                ClhScanHistoryArray.append(manufacturerData.keyAt(0),0);
            }
            ClhAdvertisedData clhAdvData = new ClhAdvertisedData();

            //add receive data to Advertise list or Process List
            //Log.i(LOG_TAG," add history"+ (receiverID>>8)+ "  "+(receiverID&0xFF) );
            //Log.i(LOG_TAG," manufacturer value"+ Arrays.toString(manufacturerData.valueAt(0)) );

            clhAdvData.parcelAdvData(manufacturerData,0);
            if(mIsSink)
            {//if this Cluster Head is the Sink node (ID=0), add data to waiting process list
                    mClhProcessData.addProcessPacketToBuffer(clhAdvData);
                    Log.i(LOG_TAG, "Add data to process list, len:" + mClhProcDataList.size());
            }
            else {//normal CLuster Head (ID 0..127) add data to advertising list to forward
                    mClhAdvertiser.addAdvPacketToBuffer(clhAdvData,false);
                    Log.i(LOG_TAG, "Add data to advertised list, len:" + mClhAdvDataList.size());
                    Log.i(LOG_TAG, "Advertise list at " + (mClhAdvDataList.size() - 1) + ":"
                            + Arrays.toString(mClhAdvDataList.get(mClhAdvDataList.size() - 1).getParcelClhData()));
            }
        }
    }

    public void setClhID(byte clhID, boolean isSink){
        mClhID=clhID;
        mIsSink=isSink;
    }

    //set alias to Clh advertiser
    public void setAdvDataObject(ClhAdvertise clhAdvObj){
        mClhAdvertiser=clhAdvObj;
        mClhAdvDataList=mClhAdvertiser.getAdvertiseList();

    }

    //set alias to Clh processor
    public void setProcDataObject(ClhProcessData clhProObj){
        mClhProcessData=clhProObj;
        mClhProcDataList=mClhProcessData.getProcessDataList();
    }

}


