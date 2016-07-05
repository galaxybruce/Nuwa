package cn.jiajixin.nuwa.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.security.auth.x500.X500Principal;

/**
 * security check
 */
public class SecurityChecker {

    private static final String CLASSES_DEX = "classes.dex";

    private static final X500Principal DEBUG_DN = new X500Principal(
            "CN=Android Debug,O=Android,C=US");

    /**
     * host publickey
     */
    private PublicKey mPublicKey;
    /**
     * host debuggable
     */
    private boolean mDebuggable;


    public SecurityChecker(Context context) {
        init(context);
    }

    private void init(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();

            PackageInfo packageInfo = pm.getPackageInfo(packageName,
                    PackageManager.GET_SIGNATURES);
            CertificateFactory certFactory = CertificateFactory
                    .getInstance("X.509");
            ByteArrayInputStream stream = new ByteArrayInputStream(
                    packageInfo.signatures[0].toByteArray());
            X509Certificate cert = (X509Certificate) certFactory
                    .generateCertificate(stream);
            mDebuggable = cert.getSubjectX500Principal().equals(DEBUG_DN);
            mPublicKey = cert.getPublicKey();
        } catch (NameNotFoundException e) {
        } catch (CertificateException e) {
        }
    }

    /**
     * @param path Apk file
     * @return true if verify apk success
     */
    public boolean verifyApk(File path) {
        if (mDebuggable) {
            return true;
        }

        JarFile jarFile = null;
        try {
            jarFile = new JarFile(path);

            JarEntry jarEntry = jarFile.getJarEntry(CLASSES_DEX);
            if (null == jarEntry) {
                return false;
            }
            loadJarEntry(jarFile, jarEntry);
            Certificate[] certs = jarEntry.getCertificates();
            if (certs == null) {
                return false;
            }
            return check(path, certs);
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (jarFile != null) {
                    jarFile.close();
                }
            } catch (IOException e) {
            }
        }
    }

    private void loadJarEntry(JarFile jarFile, JarEntry je) throws IOException {
        InputStream is = null;
        try {
            is = jarFile.getInputStream(je);
            byte[] bytes = new byte[8192];
            while (is.read(bytes) > 0) {
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private boolean check(File path, Certificate[] certs) {
        if (certs.length > 0) {
            for (int i = certs.length - 1; i >= 0; i--) {
                try {
                    certs[i].verify(mPublicKey);
                    return true;
                } catch (Exception e) {
                }
            }
        }
        return false;
    }

    public boolean checkFileMd5(File file, String md5)
    {
        FileInputStream fis = null;
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.reset();
            fis = new FileInputStream(file);
            byte[] buf = new byte[1024 * 4]; // 4k buffer
            int l;
            while ((l = fis.read(buf, 0, buf.length)) != -1) {
                m.update(buf, 0, l);
            }
            String fileMd5 = bytesToHexString(m.digest());
            NuwaLogUtils.i("aaaaaaaaaaaaaaaaa", "checkFileMd5: " + file.getAbsolutePath() + "   fileMd5: " + fileMd5);
            return fileMd5 != null && fileMd5.equals(md5);
        } catch (Exception e) {
            return false;
        }
        finally {
            if(fis != null)
            {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

}
