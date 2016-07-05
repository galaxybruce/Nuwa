package cn.jiajixin.nuwa.util;

import android.util.Log;

import cn.jiajixin.nuwa.BuildConfig;

/**
 * Created by bruce.zhang on 2016/6/27.
 */
public class NuwaLogUtils {

    public static void i(String tag, String msg) {
        if(BuildConfig.DEBUG)
        {
            Log.i(tag, msg);
        }
    }
}
