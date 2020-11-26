package no.nordicsemi.android.nrfthingy.ClusterHead;

import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;

public class ClhProcessData {

    public static final int MAX_PROCESS_LIST_ITEM=128;
    private int mMaxProcAllowable=MAX_PROCESS_LIST_ITEM;
    private ArrayList<ClhAdvertisedData> mClhProcessDataList;



    public ClhProcessData()
    {
        mClhProcessDataList=new ArrayList<ClhAdvertisedData>(MAX_PROCESS_LIST_ITEM);

    }

    public ClhProcessData(ArrayList<ClhAdvertisedData> ClhProcessDataList,int maxProcAllowable)
    {
        mMaxProcAllowable=maxProcAllowable;
        mClhProcessDataList=ClhProcessDataList;
    }

    public ArrayList<ClhAdvertisedData> getProcessDataList()
    {
        return mClhProcessDataList;
    }

    public void addProcessPacketToBuffer(ClhAdvertisedData data)
    {
        if(mClhProcessDataList.size()<mMaxProcAllowable) {
            mClhProcessDataList.add(data);
        }
    }

    private void outputTotextbox(){
        for(int i=0;i<mClhProcessDataList.size();i++)
        {
            if(i==10) break; //maximum output 10 string at one tick


        }
    }

}
