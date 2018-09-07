package cn.jiajixin.nuwa;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import cn.jiajixin.nuwa.util.NuwaLogUtils;
import cn.jiajixin.nuwa.util.SecurityChecker;

/**
 * Created by bruce.zhang on 2016/6/26.
 */
public class NuwaManager {

    private final String PATCH_NAME = "patch_%s.apk";
    private final String DEFAULT_URL = "http://appdata.galaxybruce.com/patch/query?appName=%s&appVersion=%s&device=%s&account=%s";

    public interface HotFixListener{
        public void onHotFixPatchDownloadFinish();

        public void onHotFixPatchDeleted();
    }

    /**
     * @param context
     */
    public boolean loadPatch(Context context) {
        try {
            Nuwa.initial(context);
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.load hack success");

            String appVersion = getAppVersion(context);
            String patchPath = getTestPatchPath(appVersion);
            if(!(new File(patchPath).exists()))
            {
                NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.load testPatch not exists");
                patchPath = getPatchPath(context, appVersion);
            }
            Nuwa.loadPatch(context, patchPath);
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.loadPatch success: " + patchPath);
            return true;
        } catch (NuwaException e) {
            e.printStackTrace();
            deletePatch(context);
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.loadPatch.error: " + e.getMessage());
            return false;
        }
    }

    public void checkNewPatchForRKHY(final Context context, final String account, final HotFixListener hotFixListener) {

        String url = String.format(DEFAULT_URL, "android-rkhy", getAppVersion(context), getDeviceId(context), account);
        checkNewPatch(context, url, hotFixListener);
    }

    public void checkNewPatchForB2C(final Context context, final String account, final HotFixListener hotFixListener) {

        String url = String.format(DEFAULT_URL, "android-dynamic", getAppVersion(context), getDeviceId(context), account);
        checkNewPatch(context, url, hotFixListener);
    }

    /**
     * 检测是否有新patch包
     * @param context
     * @param url
     */
    public void checkNewPatch(final Context context, final String url, final HotFixListener hotFixListener) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                if(!Nuwa.isHackDexValid(context))
                {
                    NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.checkNewPatch hack invalid");
                    return;
                }

                PatchInfo patchInfo = queryNewPatch(url);
                patchInfo.appVersion = getAppVersion(context);

                checkNewPatch(context, patchInfo, hotFixListener);
            }
        }).start();
    }

    /**
     *
     * @param context
     * @param patchInfo
     */
    private void checkNewPatch(final Context context, final PatchInfo patchInfo, final HotFixListener hotFixListener) {
        if(patchInfo == null) return;

        if (patchInfo.delete) {
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.checkNewPatch delete");
            if(getDeleteState(context))
            {
                NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.checkNewPatch last delete");
                return;
            }
            saveDeleteState(context, true);
            deletePatch(context);
            if(hotFixListener != null)
            {
                NuwaLogUtils.i("aaaaaaaaaaaaaaaa", "onHotFixPatchDeleted");
                hotFixListener.onHotFixPatchDeleted();
            }
            return;
        }
        else
        {
            saveDeleteState(context, false);
        }

        if (TextUtils.isEmpty(patchInfo.md5)) {
            return;
        }

        String patchPath = getPatchPath(context, patchInfo.appVersion);
        File file = new File(patchPath);
        if(file.exists())
        {
            SecurityChecker securityChecker = new SecurityChecker(context.getApplicationContext());
            if(securityChecker.checkFileMd5(new File(patchPath), patchInfo.md5))
            {
                NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.checkNewPatch md5 is same");
                return;
            }
        }

        downloadPatch(context, patchInfo, hotFixListener);
    }

    /**
     * 本地patch包保存目录
     *
     * @param context
     * @return
     */
    private String getDexDir(Context context) {
        File dexDir = new File(context.getFilesDir(), Nuwa.DEX_DIR);
        if (!dexDir.exists()) {
            dexDir.mkdir();// /data/data/packagename/files/nuwa
        }
        return dexDir.getAbsolutePath();
    }

    private String getPatchPath(Context context, String appVersion)
    {
        String patchName = String.format(PATCH_NAME, appVersion);
        String dexDir = getDexDir(context);
        String patchPath = dexDir + File.separator + patchName;
        return patchPath;
    }

    private String getTestPatchPath(String appVersion)
    {
        String patchName = String.format(PATCH_NAME, appVersion);
        patchName = File.separator + patchName;
        String patchPath = Environment.getExternalStorageDirectory().getAbsolutePath().concat(patchName);
        return patchPath;
    }

    private void deletePatch(Context context) {
        deleteFiles(getDexDir(context));
    }

    private void deleteFiles(String dirPath) {
        File file = new File(dirPath);
        if (file.exists()) {
            deleteFiles(file);
        }
    }

    private void deleteFiles(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                deleteFiles(files[i]);
            }
        }

        if(!file.getName().equals(Nuwa.HACK_DEX))
        {
            file.delete();
        }
    }

    private boolean copyFile(String fromFilePath, String toFilePath)
    {
        boolean isCopySuccess = false;
        File fromFile = new File(fromFilePath);
        if (!fromFile.exists() || !fromFile.isFile() || !fromFile.canRead())
        {
            return isCopySuccess;
        }
        File toFile = new File(toFilePath);
        if (!toFile.getParentFile().exists())
        {
            toFile.getParentFile().mkdirs();
        }
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try
        {
            fis = new FileInputStream(fromFile);
            fos = new FileOutputStream(toFile);

            byte[] buffer = new byte[4096];
            int c;
            while ((c = fis.read(buffer)) > 0)
            {
                fos.write(buffer, 0, c);
            }
            fis.close();
            fos.close();
            isCopySuccess = true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            closeIO(fos);
            closeIO(fis);
        }
        return isCopySuccess;
    }

    private String InputStreamTOString(InputStream in) throws Exception
    {
        ByteArrayOutputStream outStream = null;
        try
        {
            outStream = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int count = -1;
            while ((count = in.read(data, 0, 4096)) != -1) {
                outStream.write(data, 0, count);
            }
            return new String(outStream.toByteArray(), "utf-8");
        }
        finally
        {
            closeIO(outStream);
        }
    }

    private void closeIO(Closeable closeable)
    {
        if (closeable != null)
        {
            try
            {
                closeable.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private PatchInfo queryNewPatch(String urlString) {
        PatchInfo patchInfo = new PatchInfo();
        HttpURLConnection urlConnection = null;
        InputStream inputStream = null;

        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setUseCaches(false);
            urlConnection.setDoInput(true);
            urlConnection.setRequestMethod("GET");

            if (urlConnection.getResponseCode() == 200) {
                inputStream = new BufferedInputStream(urlConnection.getInputStream());
                String content = InputStreamTOString(inputStream);
                parsePatchInfo(patchInfo, content);
                NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.queryNewPatch success");
            } else {
                NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.queryNewPatch response code: " + urlConnection.getResponseCode());
            }
        } catch (Exception e) {
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.queryNewPatch error - " + e);
        } finally {
            closeIO(inputStream);
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return patchInfo;
    }

    private void downloadPatch(final Context context, final PatchInfo patchInfo, final HotFixListener hotFixListener) {
        if(patchInfo == null) return;
        if (TextUtils.isEmpty(patchInfo.url) || TextUtils.isEmpty(patchInfo.appVersion)) return;

        FileOutputStream fos = null;
        HttpURLConnection urlConnection = null;
        InputStream is = null;
        try
        {
            String patchPath = getPatchPath(context, patchInfo.appVersion);
            String tmpFilePath = patchPath + ".tmp";
            File file = new File(patchPath);
            File tmpFile = new File(tmpFilePath);
            if(tmpFile.exists()) tmpFile.delete();

            fos = new FileOutputStream(tmpFile);
            URL url = new URL(patchInfo.url);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();

            if (urlConnection.getResponseCode() == 200) {
                if(urlConnection.getContentLength() > 0)
                {
                    NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.downloadPatch.length - " + urlConnection.getContentLength());
                    is = urlConnection.getInputStream();
                    byte buf[] = new byte[1024 * 2];

                    do
                    {
                        int read = is.read(buf);
                        if (read <= 0)
                        {
                            //下载完成
                            if(file.exists()) file.delete();
                            if(tmpFile.renameTo(file) || copyFile(tmpFilePath, patchPath))
                            {
                                NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.downloadPatch.rename success");
                                if(hotFixListener != null)
                                {
                                    hotFixListener.onHotFixPatchDownloadFinish();
                                }
                                else
                                {
//                                    android.os.Process.killProcess(android.os.Process.myPid());
//                                    System.exit(0);
                                    restartApp(context);
                                }
                            }
                            tmpFile.delete();
                            break;
                        }
                        fos.write(buf, 0, read);
                    }
                    while (true);
                }
            }
            else {
                NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.downloadPatch response code- " + urlConnection.getResponseCode());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.downloadPatch error - " + e);
        }
        finally
        {
            closeIO(fos);
            closeIO(is);
            if (urlConnection != null)
            {
                urlConnection.disconnect();
            }
        }
    }

    private void parsePatchInfo(PatchInfo patchInfo, String content)
    {
        try
        {
            JSONObject jsonObject = new JSONObject(content);
            JSONArray jsonArray = jsonObject.getJSONArray("data");
            if(jsonArray != null && jsonArray.length() > 0)
            {
                JSONObject jsonData = jsonArray.getJSONObject(0);
                patchInfo.url = jsonData.optString("url");
                patchInfo.appVersion = jsonData.optString("appVersion");
                patchInfo.delete = jsonData.optBoolean("delete");
                patchInfo.md5 = jsonData.optString("md5");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private String getAppVersion(Context context)
    {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_CONFIGURATIONS);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
        }
       return "";
    }

    private String getDeviceId(Context context)
    {
        String device = null;
        try {
            TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            device = manager.getDeviceId();
            if (TextUtils.isEmpty(device))
                device = manager.getSubscriberId();
            if (TextUtils.isEmpty(device))
                device = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (TextUtils.isEmpty(device))
                device = android.os.Build.SERIAL;
        } catch (Exception e) {
        }
        return TextUtils.isEmpty(device) ? "" : device;
    }

    public static void restartApp(Context context)
    {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);

        String otherProcessName = context.getPackageName() + ":";
        List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfoList = activityManager.getRunningAppProcesses();
        if(runningAppProcessInfoList != null)
        {
            for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcessInfoList) {
                if(runningAppProcessInfo.processName.startsWith(otherProcessName))
                {
                    android.os.Process.killProcess(runningAppProcessInfo.pid);
                }
            }
        }
        else
        {
            activityManager.killBackgroundProcesses(context.getPackageName());
        }

        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        context.startActivity(mainIntent);
        System.exit(0);
//        Process.killProcess(Process.myPid());

//        PackageManager packageManager = context.getPackageManager();
//        Intent launchIntent = packageManager.getLaunchIntentForPackage(context.getPackageName());
//        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        int requestCode = (int) SystemClock.uptimeMillis();
//        PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, launchIntent,
//                PendingIntent.FLAG_CANCEL_CURRENT);
//        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent);
////                System.exit(0);
//        Process.killProcess(Process.myPid());
    }

    private void saveDeleteState(Context context, boolean delete)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        preferences.edit().putBoolean("nuwa_delete", delete).commit();
    }

    private boolean getDeleteState(Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        return preferences.getBoolean("nuwa_delete", false);
    }
}