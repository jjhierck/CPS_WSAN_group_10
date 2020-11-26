package no.nordicsemi.android.nrfthingy.ClusterHead;

import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Arrays;

public class ClhAdvertisedData   {
    private static final int SOURCE_CLH_ID_POS=0;
    private static final int PACKET_CLH_ID_POS=1;
    private static final int DEST_CLH_ID_POS=2;
    private static final int HOP_COUNT_POS=3;
    private static final int THINGY_ID_POS=4;
    private static final int THINGY_DATA_TYPE_POS=5;
    private static final int SOUND_POWER_POSH=6;
    private static final int SOUND_POWER_POSL=7;

    private static final int CLH_ARRAY_SIZE=SOUND_POWER_POSL+1;
    byte[] ClhAdvData=new byte[CLH_ARRAY_SIZE];


    public  void Copy(ClhAdvertisedData newObj) {
        byte[] clhdata=newObj.getParcelClhData();
        ClhAdvData=Arrays.copyOf( clhdata, clhdata.length);
        /*
       this.setSourceID(newObj.getSourceID());
       this.setPacketID(newObj.getPacketID());
       this.setDestClhIdPos(newObj.getDestinationID());
       this.setHopCount(newObj.getHopCounts());
        this.setThingyId(newObj.getThingyId());
        this.setThingyDataType(newObj.getThingyDataType());
        this.setSoundPower(newObj.getSoundPower());*/
    }

    public byte[] parcelAdvData(SparseArray<byte[]> manufacturerData, int index)
    {
        ClhAdvData[SOURCE_CLH_ID_POS]=(byte)(manufacturerData.keyAt(index)>>8);
        ClhAdvData[PACKET_CLH_ID_POS]=(byte)(manufacturerData.keyAt(index)&0x00FF);
        if (manufacturerData.valueAt(index)!=null) {
            System.arraycopy(manufacturerData.valueAt(index), 0,ClhAdvData,PACKET_CLH_ID_POS + 1, manufacturerData.valueAt(index).length);
        }
        /*ClhAdvData[DEST_CLH_ID_POS]=manufacturerData.valueAt(index)[0];
        ClhAdvData[HOP_COUNT_POS]=manufacturerData.valueAt(index)[1];
        ClhAdvData[THINGY_ID_POS]=manufacturerData.valueAt(index)[2];
        ClhAdvData[SOUND_POWER_POSH]=manufacturerData.valueAt(index)[3];
        ClhAdvData[SOUND_POWER_POSL]=manufacturerData.valueAt(index)[4];*/
        return ClhAdvData;
        //sourcePacketID=manufacturerData.keyAt(index);
        //soundPower=(int)(ClhAdvData[SOUND_POWER_POSH]<<8)+((int)(ClhAdvData[SOUND_POWER_POSL])&0x00FF);
    }


    public void setSourceID(byte sourceID)
    {
        ClhAdvData[SOURCE_CLH_ID_POS]= (byte) (sourceID & 0x007F);
    }
    public void setPacketID(byte packetID)
    {
        ClhAdvData[PACKET_CLH_ID_POS]=packetID;
    }
    public void setDestId(byte destID)
    {
        ClhAdvData[DEST_CLH_ID_POS]= (byte) (destID&0x7F);
    }

    public void setHopCount(byte hop)
    {
        ClhAdvData[HOP_COUNT_POS]=hop;
    }

    public void setSoundPower(int soundPower) {
        ClhAdvData[SOUND_POWER_POSH] = (byte) (soundPower >> 8);
        ClhAdvData[SOUND_POWER_POSL] = (byte) (soundPower & 0x00FF);
        Log.i("Sound power:","Sound power:"+soundPower);
        Log.i("Sound power:", "Sound power:"+ClhAdvData[SOUND_POWER_POSH]);
        Log.i("Sound power:", "Sound power:"+ClhAdvData[SOUND_POWER_POSL]);
    }

    public void setThingyId(byte id){
        ClhAdvData[THINGY_ID_POS] = (byte)(id & 0x00FF);
    }
    public void setThingyDataType(byte typeData){
        ClhAdvData[THINGY_DATA_TYPE_POS] = (byte)(typeData & 0x00FF);
    }

    public byte[] getParcelClhData()
    {
        return ClhAdvData;
    }

    public int getSourcePacketID()
    {
        return (ClhAdvData[SOURCE_CLH_ID_POS]<<8)+((int)(ClhAdvData[PACKET_CLH_ID_POS])&0x00FF);
    }

    public byte getSourceID()
    {
        return ClhAdvData[SOURCE_CLH_ID_POS];
    }
    public byte getPacketID()
    {
        return ClhAdvData[PACKET_CLH_ID_POS];
    }
    public byte getDestinationID()
    {
        return ClhAdvData[DEST_CLH_ID_POS];
    }
    public byte getHopCounts()
    {
        return ClhAdvData[HOP_COUNT_POS];
    }
    public byte getThingyId(){return ClhAdvData[THINGY_ID_POS];}
    public byte getThingyDataType(){return ClhAdvData[THINGY_DATA_TYPE_POS];}
    public int getSoundPower()
    {
        return (ClhAdvData[SOUND_POWER_POSH]<<8)+((int)(ClhAdvData[SOUND_POWER_POSL])&0x00FF);
    }

}
