package cn.jiajixin.nuwa;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;

import cn.jiajixin.nuwa.util.AssetUtils;
import cn.jiajixin.nuwa.util.DexUtils;
import cn.jiajixin.nuwa.util.SecurityChecker;

/**
 * Created by jixin.jia on 15/10/31.
 */
public class Nuwa {

    private static final String HACK_DEX = "hack.apk";
    private static final String DEX_OPT_DIR = "nuwaopt";
    public static final String DEX_DIR = "nuwa";

    public static void initial(Context context) throws NuwaException {
        File dexDir = new File(context.getFilesDir(), DEX_DIR);
        if(!dexDir.exists())
        {
            dexDir.mkdir();// /data/data/packagename/files/nuwa
        }
        String dexPath = null;
        try {
            dexPath = AssetUtils.copyAsset(context, HACK_DEX, dexDir);
        } catch (IOException e) {
            throw new NuwaException(e.getMessage());
        }

        loadPatch(context, dexPath);
    }


    public static void loadPatch(Context context, String dexPath) throws NuwaException {
        if (context == null || TextUtils.isEmpty(dexPath)) {
            throw new NuwaException("context is null");
        }
        File dexFile = new File(dexPath);
        if (!dexFile.exists()) {
            throw new NuwaException(dexPath + " is null");
        }

        SecurityChecker securityChecker = new SecurityChecker(context.getApplicationContext());
        if (!securityChecker.verifyApk(dexFile)) {
            throw new NuwaException("verifyApk failed");
        }

        File dexOptDir = new File(context.getFilesDir(), DEX_OPT_DIR);
        dexOptDir.mkdir();
        try {
            DexUtils.injectDexAtFirst(dexPath, dexOptDir.getAbsolutePath());
        } catch (Exception e) {
            throw new NuwaException(e.getMessage());
        }
    }
}
