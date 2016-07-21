package cn.jiajixin.nuwa;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

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

import cn.jiajixin.nuwa.util.NuwaLogUtils;
import cn.jiajixin.nuwa.util.SecurityChecker;

/**
 * Created by bruce.zhang on 2016/6/26.
 */
public class NuwaManager {

    private final String PATCH_NAME = "patch_%s.apk";

    public interface HotFixListener{
        public void onHotFixPatchDownloadFinish();

        public void onHotFixPatchDeleted();
    }

    /**
     * @param context
     */
    public void loadPatch(Context context) {
        try {
            Nuwa.initial(context);
            String appVersion = getAppVersion(context);
            String patchPath = getPatchPath(context, appVersion);
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.loadPatch: " + patchPath);
            Nuwa.loadPatch(context, patchPath);
        } catch (NuwaException e) {
            e.printStackTrace();
            deletePatch(context);
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "NuwaManager.loadPatch.e: " + e.getMessage());
        }
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
                PatchInfo patchInfo = checkNewPatch(url);
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
    public void checkNewPatch(final Context context, final PatchInfo patchInfo, final HotFixListener hotFixListener) {
        if(patchInfo == null) return;

        if (patchInfo.delete) {
            deletePatch(context);
            if(hotFixListener != null)
            {
                hotFixListener.onHotFixPatchDeleted();
            }
            return;
        }

        if (TextUtils.isEmpty(patchInfo.md5)) {
            return;
        }

        String patchPath = getPatchPath(context, patchInfo.appVersion);
        File file = new File(patchPath);
        if(file.exists())
        {
            SecurityChecker securityChecker = new SecurityChecker(context.getApplicationContext());
            if(securityChecker.checkFileMd5(new File(patchPath), patchInfo.md5)) return;
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
        file.delete();
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

    private PatchInfo checkNewPatch(String urlString) {
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
            } else {
                NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "checkNewPatch checkNewPatch " + urlConnection.getResponseCode());
            }
        } catch (Exception e) {
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "checkNewPatch - " + e);
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
                    NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "downloadPatch.length - " + urlConnection.getContentLength());
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
                                NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "downloadPatch.rename success");
                                if(hotFixListener != null)
                                {
                                    hotFixListener.onHotFixPatchDownloadFinish();
                                }
                                else
                                {
                                    android.os.Process.killProcess(android.os.Process.myPid());
                                    System.exit(0);
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
        }
        catch (Exception e)
        {
            e.printStackTrace();
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "Error in downloadPatch - " + e);
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
            JSONObject jsonData = jsonObject.getJSONObject("data");
            patchInfo.url = jsonData.optString("url");
            patchInfo.appVersion = jsonData.optString("appVersion");
            patchInfo.delete = jsonData.optBoolean("delete");
            patchInfo.md5 = jsonData.optString("md5");
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

}