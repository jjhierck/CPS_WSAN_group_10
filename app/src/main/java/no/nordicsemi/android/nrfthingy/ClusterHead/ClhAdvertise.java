package no.nordicsemi.android.nrfthingy.ClusterHead;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.CountDownTimer;
import android.os.ParcelUuid;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;


public class ClhAdvertise {

    private final static String CLH_NAME=ClhConst.clusterHeadName; //common name for indentifying a Cluster Head
    private final String LOG_TAG = "CLH Advertising"; //Tag for debug logging via USB
    public final static int ADV_SETTING_BYTE_MODE = 0;
    public final static int ADV_SETTING_BYTE_SENDNAME = 1;
    public final static int ADV_SETTING_BYTE_SENDTXPOWER = 2;


    public final static int ADV_SETTING_MODE_LOWPOWER = 0;
    public final static int ADV_SETTING_MODE_BALANCE = 1;
    public final static int ADV_SETTING_MODE_LOWLATENCY = 2;

    public final static int ADV_SETTING_SENDNAME_NO = 0;
    public final static int ADV_SETTING_SENDNAME_YES = 1;

    public final static int ADV_SETTING_SENDTXPOWER_NO = 0;
    public final static int ADV_SETTING_SENDTXPOWER_YES = 1;

    private static String mUUId = null;
    private static int mUniqueManuID=1510;
    private static ParcelUuid mPUUID;
    private static BluetoothLeAdvertiser mAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();

    private final static int BLE_CLH_ADVERTISING_STATUS_DISABLE=256;
    private final static int BLE_CLH_ADVERTISING_STATUS_STOP=0;
    private final static int BLE_CLH_ADVERTISING_STATUS_START=1;
    private final static int BLE_CLH_ADVERTISING_STATUS_NO_DATA=2;
    private final static int BLE_CLH_ADVERTISING_STATUS_STOP_WAIT=100;
    private final static long MAX_ADVERTISING_INTERVAL=10000;  //max 10s for an advertising packet interval
    private final static int MAX_ADVERTISE_LIST_ITEM=64; //max queue list for advertising


    private int mMaxAdvAllowable=MAX_ADVERTISE_LIST_ITEM;
    private int mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_DISABLE;
    private CountDownTimer mAdvertisingTimer;
    private long mAdvInterval=0;
    private byte[] mAdvsettings=new byte[32];
    private byte mClhID=1;
    private boolean mIsSink=false;
    private byte mCurrentPacketID= (byte) 1;
    private ArrayList<ClhAdvertisedData >mClhAdvDataList;

    public ClhAdvertise(){//constructor with no params
        mClhAdvDataList= new ArrayList<ClhAdvertisedData>(MAX_ADVERTISE_LIST_ITEM);
    }

    //constructor with param
    public ClhAdvertise(ArrayList<ClhAdvertisedData> clhAdvDataList, int maxAdvAllowable){
        mMaxAdvAllowable=maxAdvAllowable;
        mClhAdvDataList=clhAdvDataList;
    }



    public int initCLHAdvertiser()
    {
        int error;
        byte[] advsettings=new byte[16];
        Log.i(LOG_TAG, "Start Intialize func");
        if ((error=checkBLEAdvertiser())!=ClhErrors.ERROR_CLH_NO)
            return error;


        if (mUUId == null) {
            //get random UUID
            mUUId = UUID.randomUUID().toString().toUpperCase();
            mPUUID = new ParcelUuid(UUID.fromString(mUUId));

            if (!BluetoothAdapter.getDefaultAdapter().setName(ClhConst.clusterHeadName)) {
                Log.i(LOG_TAG, "Advertiser: set name fail" );
                return ClhErrors.ERROR_CLH_BLE_SETNAME_FAIL;
            }
            Log.i(LOG_TAG, "Set Name:" +BluetoothAdapter.getDefaultAdapter().getName());

        }
        //start default advertising: low power, name and UUID , no TX power
        advsettings[ADV_SETTING_BYTE_MODE] = ADV_SETTING_MODE_LOWPOWER;
        advsettings[ADV_SETTING_BYTE_SENDNAME] = ADV_SETTING_SENDNAME_YES;
        advsettings[ADV_SETTING_BYTE_SENDTXPOWER] = ADV_SETTING_SENDTXPOWER_NO;

        //on advertising -> stop advertiser
        if(mBleClhAdvertisingStatus==BLE_CLH_ADVERTISING_STATUS_START)  stopAdvertiseClhData();

        //set up timer for each packet advertising, expire interval in mAdvInterval
        mAdvertisingTimer=new CountDownTimer(mAdvInterval,100) {
            @Override
            public void onTick(long millisUntilFinished) {//tick, not used
            }

            @Override
            public void onFinish() {
                if((mBleClhAdvertisingStatus==BLE_CLH_ADVERTISING_STATUS_STOP_WAIT)||
                        (mBleClhAdvertisingStatus==BLE_CLH_ADVERTISING_STATUS_STOP))
                {
                    mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP;
                }
                else
                {
                    mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP;
                    mAdvertiser.stopAdvertising(advertisingCallback);
                    nextAdvertisingPacket(); //advertise next packet
                }
            }
        };
        Log.i(LOG_TAG,"End Initializing func");

        //start
        //byte[] data={3};
        //startAdvertiser(advsettings,data);
        return ClhErrors.ERROR_CLH_NO;
    }

    /*update an advertiser with new data
    1st stop the current advertising
    2nd check data and previous settings
    3nd restart advertiser for new data and previous settings
     */
    public int updateCLHdata(byte data[])
    {
        int error;
        byte[] advData= new byte[256];
        int length;

        Log.i(LOG_TAG, "Update Advertised Data func");
        if ((error=checkBLEAdvertiser())!=ClhErrors.ERROR_CLH_NO)
            return error;

        if (data==null)
        {
            return ClhErrors.ERROR_CLH_ADV_NO_DATA;
        }
        else
        {
            length=Math.min(data.length,advData.length);
            advData= Arrays.copyOfRange(data,0, length);
        }

        if(mAdvsettings==null)
        {
            return ClhErrors.ERROR_CLH_ADV_NOT_YET_SETTING;
        }

        Log.i(LOG_TAG, "Advertiser: update data");
        stopAdvertiseClhData();
        error=startAdvertiser(mAdvsettings,advData);

        Log.i(LOG_TAG, "End update data func");
        return error;

    }


    //------------------------
    // advertising next data in the waiting list
    public void nextAdvertisingPacket(){

        if (mClhAdvDataList.size()>0)
        {//list not empty, advertise item 0 in the list
            byte[] mAdvData = mClhAdvDataList.get(0).getParcelClhData();
            mClhAdvDataList.remove(0);
            updateCLHdata(mAdvData);
            Log.i(LOG_TAG,"new data: size:"+mClhAdvDataList.size() + ",data:" +Arrays.toString(mAdvData));
        }
        else
        {//empty list
            mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_NO_DATA;
            mAdvertisingTimer.start(); // start timer to periodly check (100ms) the list later
        }
    }

    /*==========================
    Add a packet to queuing buffer for advertising
    @parma:
    data: data to be advertising
    isOrginal: =true: packet is from internal process of this cluster head.
            =false: packet received from other cluster head, need forwarding
     */
    public void addAdvPacketToBuffer(ClhAdvertisedData data,boolean isOrginal)
    {
        if(mClhAdvDataList.size()<mMaxAdvAllowable) {
            if(isOrginal) {//this packet come from this device-> increase PacketID
                mCurrentPacketID++;
                data.setPacketID(mCurrentPacketID);
            }
            else
            {//received packet over BLE scan, from other cluster head -> increase hopscount
                byte hopcounts=data.getHopCounts();
                hopcounts++;
                data.setHopCount(hopcounts);
            }
            mClhAdvDataList.add(data);
            Log.i(LOG_TAG,"add Adv packet, size:"+mClhAdvDataList.size());
        }
    }


    //----------------------------------------------------
    // parcel Sound data and add to waiting list for advertising
    private static int mSoundcount=0;
    public void addAdvSoundData(byte[]data)
    {

        if((data!=null) && data.length>0) {
            //in this demo, only the first data from the sound stream is used for sending
            byte[] arr=new byte[4];
            arr[3]=data[0];
            arr[2]=data[1];
            arr[0]=arr[1] = 0;
            int sounddata=ByteBuffer.wrap(arr).getInt();
            if (sounddata>32767) sounddata=sounddata-65535;
            //Log.i(LOG_TAG,"sound data:"+sounddata);

            if(mSoundcount++==100)
            {//wait 100 dataset to reduce the load, update the sound data to advertising list
                ClhAdvertisedData advData = new ClhAdvertisedData();
                advData.setSourceID(mClhID);
                advData.setDestId((byte) 0);
                advData.setThingyDataType((byte) 10);
                advData.setThingyId((byte) 1);
                advData.setHopCount((byte) 0);
                advData.setSoundPower(sounddata);
                addAdvPacketToBuffer(advData,true);
                ClhAdvertisedData temp=mClhAdvDataList.get(mClhAdvDataList.size()-1);
                Log.i(LOG_TAG,"add new sound data:"+ Arrays.toString( temp.getParcelClhData()));
                mSoundcount=0;
            }
        }
    }

    /*----------
 Start advertising "data"
 @param
  data[]: data
  settings[]: advertising mode
             [0]: ADV_SETTING_BYTE_ENERGY,Energy mode:
                     LOW_POWER_MODE ADV_SETTING_ENERGY_LOWPOWER (default)
                     BALANCE_MODE ADV_SETTING_ENERGY_BALANCE
                     HIGH_LATENCY_MODE ADV_SETTING_ENERGY_LOWLATENCY
             [1]: ADV_SETTING_BYTE_SENDNAME,send name:
                 0: ADV_SETTING_SENDNAME_NO
                 1: ADV_SETTING_SENDNAME_YES (default)
             [2]: ADV_SETTING_BYTE_SENDTXPOWER,send TxPower
                 0: ADV_SETTING_SENDTXPOWER_NO (default)
                 1: ADV_SETTING_SENDTXPOWER_YES
 --------*/
    private int startAdvertiser(byte[] settings, byte[] data) {
        //setting and start advertiser
        //@param: settings: configuration
        //data: input data [0]: length
        //  if lenght =0: send UUID only

        Log.i(LOG_TAG,"Start Start Advertizer func");

        AdvertiseSettings.Builder advSettingsBuilder = new AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false);
        //set operation mode: low energy to latency
        switch (settings[ADV_SETTING_BYTE_MODE]) {
            case ADV_SETTING_MODE_LOWLATENCY:
                advSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
                break;
            case ADV_SETTING_MODE_BALANCE:
                advSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
                break;
            default:
                advSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
                break;
        }
        AdvertiseSettings advSettings = advSettingsBuilder.build();

        //enable/disable send device name and txpower
        int advDatalen=3; //count the length of advertise data
        AdvertiseData.Builder advDataBuilder = new AdvertiseData.Builder();
        if (settings[ADV_SETTING_BYTE_SENDNAME] == ADV_SETTING_SENDNAME_NO){
            advDataBuilder.setIncludeDeviceName(false);
        }
        else{
            advDataBuilder.setIncludeDeviceName(true);
            advDatalen=BluetoothAdapter.getDefaultAdapter().getName().length()+2;
        }

        if (settings[ADV_SETTING_BYTE_SENDTXPOWER] == ADV_SETTING_SENDTXPOWER_YES) {
            advDataBuilder.setIncludeTxPowerLevel(true);
            advDatalen+=3;
        } else {
            advDataBuilder.setIncludeTxPowerLevel(false);
        }

        if(data.length<2)
        {
            Log.i(LOG_TAG, "send UUID only: "+mPUUID);
            advDataBuilder.addServiceUuid(mPUUID);
        }
        else
        {
            Log.i(LOG_TAG, "current length: "+ advDatalen);
            advDatalen=data.length+advDatalen+3 + 2 ; // include: 3(default) + name (vary:option) + txpower (3:option)
            // + 2(setting for manufacturer) + data
            if(advDatalen>31)
            {//if data length too long, send UUID
                Log.i(LOG_TAG, "Too long advertise data:" + advDatalen);
                return ClhErrors.ERROR_CLH_ADV_TOO_LONG_DATA;
            }
            else
            {
                int len=data.length;
                int manuSpec=(((int)data[0]<<8)&0x7F00)+ ((int)(data[1])&0x00FF);
                byte[] advData= Arrays.copyOfRange(data,2, len);
                advDataBuilder.addManufacturerData(manuSpec,advData);
                Log.i(LOG_TAG, "send manufature data, total length:" +advDatalen);
                Log.i(LOG_TAG, "send data length:" +len);
                Log.i(LOG_TAG, "Manu Spec: 0x" + data[0] + ","+data[1]);
                Log.i(LOG_TAG, "Manu Data: "+ Arrays.toString(advData));
            }
        }
        AdvertiseData sendData = advDataBuilder.build();
        mAdvertiser.startAdvertising(advSettings, sendData, null, advertisingCallback);


        Log.i(LOG_TAG,"End Start advertizer func");

        return ClhErrors.ERROR_CLH_NO;
    }

    public void stopAdvertiseClhData()
    {

        Log.i(LOG_TAG, "Stop CLH Advertiser func");
        mAdvertiser.stopAdvertising(advertisingCallback);
        if(mAdvertisingTimer!=null){
            mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP_WAIT;
            mAdvertisingTimer.cancel();//stop timer
        }
        else{
            mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP;
        }
        Log.i(LOG_TAG, "End Stop CLH Advertizer func");
    }

    private final AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.i(LOG_TAG, "Start Advertising Success "+ settingsInEffect.describeContents());
            mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_START;
            mAdvertisingTimer.start();//start timer for next packet
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.i(LOG_TAG, "Advertising onStartFailure: " + errorCode);
            mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP;
        }
    };

    private int checkBLEAdvertiser()
    {
        //verify BLE available
        if (!BluetoothAdapter.getDefaultAdapter().isMultipleAdvertisementSupported()) {
            Log.i(LOG_TAG, "Multiple advertisement not supported");
            return ClhErrors.ERROR_CLH_ADV_MULTI_ADVERTISER;
        }
        if ((mAdvertiser=BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser()) == null) {
            Log.i(LOG_TAG, "BLE not supported");
            return ClhErrors.ERROR_CLH_BLE_NOT_ENABLE;
        }
        return ClhErrors.ERROR_CLH_NO;
    }

    public void clearAdvList()
    {
        mClhAdvDataList.clear();
    }

    //set Cluster head ID
    public void setAdvClhID(byte clhID, boolean isSink){  //set Cluster Head ID for advertiser
        mClhID=clhID;
        mIsSink=isSink;
    }

    public void setAdvSettings(byte[] settings)
    {
        Log.i(LOG_TAG, "Start Setting Advertizing params func");

        if(settings==null)
        {   // default advertising: low power, name and UUID , no TX power
            mAdvsettings[ADV_SETTING_BYTE_MODE] = ADV_SETTING_MODE_LOWPOWER;
            mAdvsettings[ADV_SETTING_BYTE_SENDNAME] = ADV_SETTING_SENDNAME_YES;
            mAdvsettings[ADV_SETTING_BYTE_SENDTXPOWER] = ADV_SETTING_SENDTXPOWER_NO;

        }
        else{
            int len = Math.min(mAdvsettings.length, settings.length);
            mAdvsettings=Arrays.copyOfRange(settings,0,len);
        }
        Log.i(LOG_TAG, "End Setting Advertizing params func");
    }

    public void setAdvInterval(long interval)
    {
        if (interval<=0)
        {
            mAdvInterval=MAX_ADVERTISING_INTERVAL;
        }
        else
        {
            mAdvInterval=interval;
        }
    }

    public ArrayList<ClhAdvertisedData> getAdvertiseList()
    {
        return mClhAdvDataList;
    }

    public final byte[] getAdvSettings()
    {
        return mAdvsettings;
    }
}
