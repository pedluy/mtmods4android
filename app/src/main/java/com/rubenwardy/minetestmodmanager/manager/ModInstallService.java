package com.rubenwardy.minetestmodmanager.manager;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.os.ResultReceiver;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.rubenwardy.minetestmodmanager.R;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mod manager service.
 */
public class ModInstallService extends IntentService {
    private static final int UPDATE_PROGRESS = 8344;
    private static final String ACTION_URL_INSTALL = "com.rubenwardy.minetestmodmanager.action.URL_INSTALL";
    public static final String ACTION_INSTALL = "com.rubenwardy.minetestmodmanager.action.INSTALL";
    public static final String ACTION_UNINSTALL = "com.rubenwardy.minetestmodmanager.action.UNINSTALL";
    public static final String ACTION_FETCH_SCREENSHOT = "com.rubenwardy.minetestmodmanager.action.FETCH_SCREENSHOT";
    public static final String ACTION_FETCH_MODLIST = "com.rubenwardy.minetestmodmanager.action.FETCH_MODLIST";
    private static final String ACTION_REPORT = "com.rubenwardy.minetestmodmanager.action.REPORT";

    private static final String EXTRA_MOD_NAME = "com.rubenwardy.minetestmodmanager.extra.MOD_NAME";
    private static final String EXTRA_AUTHOR = "com.rubenwardy.minetestmodmanager.extra.AUTHOR";
    private static final String EXTRA_URL = "com.rubenwardy.minetestmodmanager.extra.URL";
    private static final String EXTRA_DEST = "com.rubenwardy.minetestmodmanager.extra.DEST";
    private static final String EXTRA_LIST = "com.rubenwardy.minetestmodmanager.extra.LIST";
    private static final String EXTRA_LINK = "com.rubenwardy.minetestmodmanager.extra.LINK";
    private static final String EXTRA_REASON = "com.rubenwardy.minetestmodmanager.extra.REASON";
    private static final String EXTRA_INFO = "com.rubenwardy.minetestmodmanager.extra.INFO";
    public static final String RET_ACTION = "action";
    public static final String RET_AUTHOR = "author";
    public static final String RET_NAME = "name";
    public static final String RET_DEST = "dest";
    public static final String RET_ERROR = "error";

    public ModInstallService() {
        super("ModInstallService");
    }

    private boolean isValidModname(String str) {
        for (int i = 0; i < str.length(); ++i) {
            char c = str.charAt(i);
            if (!Character.isDigit(c) && !Character.isLetter(c) && c != '_' && c != '-') {
                return false;
            }
        }

        return true;
    }

    @MainThread
    public static void startActionInstall(@NonNull Context context, ServiceResultReceiver srr,
            @NonNull String modname, @Nullable String author, @NonNull File zip, String dest) {
        Intent intent = new Intent(context, ModInstallService.class);
        intent.setAction(ACTION_INSTALL);
        intent.putExtra(EXTRA_MOD_NAME, modname);
        intent.putExtra(EXTRA_AUTHOR, author);
        intent.putExtra(EXTRA_URL, zip.getAbsolutePath());
        intent.putExtra(EXTRA_DEST, dest);
        intent.putExtra("receiverTag", srr);
        context.startService(intent);
    }

    @MainThread
    public static void startActionUrlInstall(@NonNull Context context, ServiceResultReceiver srr,
                                             @NonNull String modname, @Nullable String author, String url, String dest) {
        Intent intent = new Intent(context, ModInstallService.class);
        intent.setAction(ACTION_URL_INSTALL);
        intent.putExtra(EXTRA_MOD_NAME, modname);
        intent.putExtra(EXTRA_AUTHOR, author);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_DEST, dest);
        intent.putExtra("receiverTag", srr);
        context.startService(intent);
    }


    @MainThread
    public static void startActionFetchScreenshot(@NonNull Context context, ServiceResultReceiver srr,
                                             @NonNull String modname, @Nullable String author) {
        Intent intent = new Intent(context, ModInstallService.class);
        intent.setAction(ACTION_FETCH_SCREENSHOT);
        intent.putExtra(EXTRA_MOD_NAME, modname);
        intent.putExtra(EXTRA_AUTHOR, author);
        intent.putExtra("receiverTag", srr);
        context.startService(intent);
    }
    @MainThread
    public static void startActionUninstall(@NonNull Context context, ServiceResultReceiver srr,
                                            String modname, String path) {
        Intent intent = new Intent(context, ModInstallService.class);
        intent.setAction(ACTION_UNINSTALL);
        intent.putExtra(EXTRA_MOD_NAME, modname);
        intent.putExtra(EXTRA_DEST, path);
        intent.putExtra("receiverTag", srr);
        context.startService(intent);
    }

    @MainThread
    public static void startActionFetchModList(@NonNull Context context, ServiceResultReceiver srr,
                                               String url) {
        Intent intent = new Intent(context, ModInstallService.class);
        intent.setAction(ACTION_FETCH_MODLIST);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra("receiverTag", srr);
        context.startService(intent);
    }

    @MainThread
    public static void startActionReport(@NonNull Context context, ServiceResultReceiver srr,
                                         @NonNull String modname, @Nullable String author,
                                         @Nullable String list, @Nullable String link,
                                         @NonNull String reason, @NonNull String info) {
        Intent intent = new Intent(context, ModInstallService.class);
        intent.setAction(ACTION_REPORT);
        intent.putExtra(EXTRA_MOD_NAME, modname);
        intent.putExtra(EXTRA_AUTHOR, author);
        intent.putExtra(EXTRA_LIST, list);
        intent.putExtra(EXTRA_LINK, link);
        intent.putExtra(EXTRA_REASON, reason);
        intent.putExtra(EXTRA_INFO, info);
        intent.putExtra("receiverTag", srr);
        context.startService(intent);
    }

    private static final AtomicBoolean requestStop = new AtomicBoolean();
    public static void cancelCurrentTask() {
        requestStop.set(true);
    }

    @Override
    @WorkerThread
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            requestStop.set(false);
            final String action = intent.getAction();
            ResultReceiver rec = intent.getParcelableExtra("receiverTag");

            switch (action) {
            case ACTION_INSTALL: {
                final String modname = intent.getStringExtra(EXTRA_MOD_NAME);
                final String author = intent.getStringExtra(EXTRA_AUTHOR);
                final String zippath = intent.getStringExtra(EXTRA_URL);
                final String dest = intent.getStringExtra(EXTRA_DEST);
                handleActionInstall(rec, modname, author, new File(zippath), new File(dest));
                break;
            }
            case ACTION_FETCH_MODLIST: {
                final String url = intent.getStringExtra(EXTRA_URL);
                handleActionFetchModList(rec, url);
                break;
            }
            case ACTION_URL_INSTALL: {
                final String modname = intent.getStringExtra(EXTRA_MOD_NAME);
                final String author = intent.getStringExtra(EXTRA_AUTHOR);
                final String url = intent.getStringExtra(EXTRA_URL);
                final String dest = intent.getStringExtra(EXTRA_DEST);
                handleActionUrlInstall(rec, modname, author, url, new File(dest));
                break;
            }
            case ACTION_FETCH_SCREENSHOT: {
                final String modname = intent.getStringExtra(EXTRA_MOD_NAME);
                final String author = intent.getStringExtra(EXTRA_AUTHOR);
                handleActionFetchScreenshot(rec, modname, author);
                break;
            }
            case ACTION_REPORT: {
                final String modname = intent.getStringExtra(EXTRA_MOD_NAME);
                final String author = intent.getStringExtra(EXTRA_AUTHOR);
                final String list = intent.getStringExtra(EXTRA_LIST);
                final String link = intent.getStringExtra(EXTRA_LINK);
                final String reason = intent.getStringExtra(EXTRA_REASON);
                final String info = intent.getStringExtra(EXTRA_INFO);

                handleActionReport(rec, modname, author, list, link, reason, info);
                break;
            }
            case ACTION_UNINSTALL:
                // TODO: FIXME: This is a potential security problem!

                final String modname = intent.getStringExtra(EXTRA_MOD_NAME);
                final String path = intent.getStringExtra(EXTRA_DEST);

                if (isValidModname(modname)) {
                    Utils.deleteRecursive(new File(path, modname));

                    if ((new File(path, modname)).exists()) {
                        Bundle b = new Bundle();
                        b.putString(RET_ACTION, ACTION_UNINSTALL);
                        b.putString(RET_NAME, modname);
                        b.putString(RET_DEST, path);
                        b.putString(RET_ERROR, "Folder still exists after attempting to delete.");
                        rec.send(0, b);
                    } else {
                        Bundle b = new Bundle();
                        b.putString(RET_ACTION, ACTION_UNINSTALL);
                        b.putString(RET_NAME, modname);
                        b.putString(RET_DEST, path);
                        rec.send(0, b);
                    }
                } else {
                    Bundle b = new Bundle();
                    b.putString(RET_ACTION, ACTION_UNINSTALL);
                    b.putString(RET_NAME, modname);
                    b.putString(RET_DEST, path);
                    b.putString(RET_ERROR, "Invalid request to Service: invalid modname");
                    rec.send(0, b);
                }

                break;
            default:
                Log.e("ModService", "Invalid action request.");
                break;
            }
        }
    }

    @WorkerThread
    private void handleActionReport(@NonNull ResultReceiver rec, @NonNull String modname,
                                        @Nullable String author, @Nullable String list,
                                        @Nullable String link, @NonNull String reason,
                                        @NonNull String info) {
        try {
            String urlParameters  = "modname=" + URLEncoder.encode(modname, "UTF-8");
            urlParameters += "&msg=" + URLEncoder.encode(info, "UTF-8");
            urlParameters += "&reason=" + URLEncoder.encode(reason, "UTF-8");
            if (author != null) {
                urlParameters += "&author=" + URLEncoder.encode(author, "UTF-8");
            }
            if (list  != null) {
                urlParameters += "&list=" + URLEncoder.encode(list, "UTF-8");
            }
            if (link  != null) {
                urlParameters += "&link=" + URLEncoder.encode(link, "UTF-8");
            }

            byte[] postData       = urlParameters.getBytes(Charset.forName("UTF-8"));
            int    postDataLength = postData.length;

            URL url = new URL("http://app-mtmm.rubenwardy.com/v1/report/");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);

            DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
            wr.write(postData);
            wr.flush();
            wr.close();

            int responseCode = conn.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void reportDownloadToServer(@NonNull String modname, @Nullable String author,
                                        @NonNull String url_str, int size, int statusCode,
                                        @Nullable String error) {
        try {
            String urlParameters  = "modname=" + URLEncoder.encode(modname, "UTF-8");
            urlParameters += "&link=" + URLEncoder.encode(url_str, "UTF-8");
            urlParameters += "&size=" + size;
            urlParameters += "&status=" + statusCode;
            if (author != null) {
                urlParameters += "&author=" + URLEncoder.encode(author, "UTF-8");
            }
            if (error != null) {
                urlParameters += "&error=" + URLEncoder.encode(error, "UTF-8");
            }
            byte[] postData       = urlParameters.getBytes(Charset.forName("UTF-8"));
            int    postDataLength = postData.length;

            URL url = new URL("http://app-mtmm.rubenwardy.com/v1/on-download/");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);

            DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
            wr.write(postData);
            wr.flush();
            wr.close();

            int responseCode = conn.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void handleActionUrlInstall(@NonNull ResultReceiver rec, @NonNull String modname,
                                        @Nullable String author, @NonNull String url_str,
                                        @NonNull File dest) {
        NotificationManager notiman =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        try {
            // Resource
            String str_installing = "ERR! Installing $1...";
            String str_connecting = "ERR! Connecting...";
            String str_downloading = "ERR! Downloading...";
            String str_extracting = "ERR! Extracting...";
            Resources res = getApplicationContext().getResources();
            if (res != null) {
                str_installing = String.format(res.getString(R.string.installing), modname);
                str_connecting = res.getString(R.string.connecting);
                str_downloading = res.getString(R.string.downloading);
                str_extracting = res.getString(R.string.extracting);
            }

            // Notification
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
            mBuilder.setContentTitle(str_installing)
                    .setContentText(str_connecting)
                    .setSmallIcon(R.drawable.notification_icon);
            mBuilder.setProgress(0, 0, true);
            Intent intent = new Intent(this, BCastReceiver.class);
            intent.setAction(BCastReceiver.ACTION_STOP_SERVICE);
            PendingIntent contentIntent = PendingIntent.getBroadcast(getBaseContext(), 0, intent, 0);
            mBuilder.setContentIntent(contentIntent);
            notiman.notify(1337, mBuilder.build());

            //
            // PROBE
            //
            URL url = new URL(url_str);
            {
                // Open HEAD http connection
                HttpURLConnection testcon = (HttpURLConnection)url.openConnection();
                testcon.setRequestMethod("HEAD");
                testcon.connect();

                // Check for existence
                int code = testcon.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    String contentType = testcon.getContentType();
                    if (!contentType.equals("application/octet-stream") &&
                            !contentType.equals("application/zip") &&
                            !contentType.startsWith("application/x-zip")) {
                        Bundle b = new Bundle();
                        b.putString(RET_NAME, modname);
                        b.putString(RET_ACTION, ACTION_INSTALL);
                        b.putString(RET_DEST, dest.getAbsolutePath());
                        b.putString(RET_ERROR, "Failed to download: File is not a zip file");
                        rec.send(0, b);

                        notiman.cancel(1337);
                        reportDownloadToServer(modname, author, url_str, 0, code,
                                "wrong-content-" + testcon.getContentType());
                        return;
                    }
                } else if (code == HttpURLConnection.HTTP_BAD_METHOD) {
                    Log.w("ModService", "UrlInstall: Server does not accept HEAD.");
                } else {
                    Bundle b = new Bundle();
                    b.putString(RET_NAME, modname);
                    b.putString(RET_ACTION, ACTION_INSTALL);
                    b.putString(RET_DEST, dest.getAbsolutePath());
                    if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                        b.putString(RET_ERROR, "Failed to download: 404 File not found.");
                    } else {
                        b.putString(RET_ERROR, "Failed to download: " + code + " Error");
                    }
                    rec.send(0, b);

                    notiman.cancel(1337);
                    reportDownloadToServer(modname, author, url_str, 0, code, "wrong-status");
                    return;
                }

                // TODO: Check size
                /*testcon.getInputStream();
                int size = testcon.getContentLength();
                testcon.disconnect();*/
            }

            //
            // DOWNLOAD
            //
            {

                // Start download
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                connection.connect();

                // Check Response Code
                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    Bundle b = new Bundle();
                    b.putString(RET_NAME, modname);
                    b.putString(RET_ACTION, ACTION_INSTALL);
                    b.putString(RET_DEST, dest.getAbsolutePath());
                    if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                        b.putString(RET_ERROR, "Failed to download: 404 File not found.");
                    } else {
                        b.putString(RET_ERROR, "Failed to download: " + code + " Error");
                    }
                    rec.send(0, b);

                    notiman.cancel(1337);
                    reportDownloadToServer(modname, author, url_str, 0, code, "wrong-status");
                    return;
                }

                // Check content type
                String contentType = connection.getContentType();
                if (!contentType.equals("application/octet-stream") &&
                        !contentType.equals("application/zip") &&
                        !contentType.startsWith("application/x-zip")) {
                    connection.disconnect();

                    Bundle b = new Bundle();
                    b.putString(RET_NAME, modname);
                    b.putString(RET_ACTION, ACTION_INSTALL);
                    b.putString(RET_DEST, dest.getAbsolutePath());
                    b.putString(RET_ERROR, "Failed to download: File is not a zip file");
                    rec.send(0, b);

                    notiman.cancel(1337);
                    reportDownloadToServer(modname, author, url_str, 0, code,
                            "wrong-content-" + connection.getContentType());
                    return;
                }

                // Update notification
                mBuilder.setContentText(str_downloading);
                notiman.notify(1337, mBuilder.build());

                // download the file
                File file = Utils.getTmpPath(getCacheDir(), ".zip");
                InputStream input = new BufferedInputStream(connection.getInputStream());
                OutputStream output = new FileOutputStream(file);
                int fileLength = connection.getContentLength();
                byte data[] = new byte[1024];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);

                    int prog = (int) (total * 100 / fileLength);
                    total += count;

                    // Update Notification
                    mBuilder.setProgress(100, prog, false);
                    notiman.notify(1337, mBuilder.build());

                    // Detect cancel
                    if (requestStop.get()) {
                        input.close();
                        output.close();
                        notiman.cancel(1337);
                        Bundle b = new Bundle();
                        b.putString(RET_NAME, modname);
                        b.putString(RET_ACTION, ACTION_INSTALL);
                        b.putString(RET_DEST, dest.getAbsolutePath());
                        b.putString(RET_ERROR, "Cancelled download.");
                        rec.send(0, b);
                        return;
                    }
                }
                output.flush();
                output.close();
                input.close();

                // Update Notification
                mBuilder.setProgress(0, 0, true);
                mBuilder.setContentText(str_extracting);
                notiman.notify(1337, mBuilder.build());

                handleActionInstall(rec, modname, author, file.getAbsoluteFile(), dest);

                reportDownloadToServer(modname, author, url_str, connection.getContentLength(), 200, null);
                try {
                    if (!file.delete()) {
                        Log.e("ModService", "Failed to delete tmp zip file");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            notiman.cancel(1337);
        } catch (IOException e) {
            notiman.cancel(1337);
            Bundle b = new Bundle();
            b.putString(RET_NAME, modname);
            b.putString(RET_ACTION, ACTION_INSTALL);
            b.putString(RET_DEST, dest.getAbsolutePath());
            b.putString(RET_ERROR, "Failed to download: " + e.toString());
            rec.send(0, b);
            e.printStackTrace();
        }
    }

    private void doFetchScreenshotUrl(@NonNull ResultReceiver rec, @NonNull String modname,
                                      @Nullable String author, @NonNull String url_str, int try_i) {
        if (try_i > 10) {
            Bundle b = new Bundle();
            b.putString(RET_NAME, modname);
            b.putString(RET_ACTION, ACTION_FETCH_SCREENSHOT);
            b.putString(RET_ERROR, "Failed to download: too many redirects");
            rec.send(0, b);
            return;
        }

        try {
            URL url = new URL(url_str);

            // Start download
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.connect();

            // Check Response Code
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_SEE_OTHER) {
                doFetchScreenshotUrl(rec, modname, author, connection.getHeaderField("Location"), try_i + 1);
                return;
            } else if (code != HttpURLConnection.HTTP_OK) {
                Bundle b = new Bundle();
                b.putString(RET_NAME, modname);
                b.putString(RET_ACTION, ACTION_FETCH_SCREENSHOT);
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    b.putString(RET_ERROR, "Failed to download: 404 File not found.");
                } else {
                    b.putString(RET_ERROR, "Failed to download: " + code + " Error");
                }
                rec.send(0, b);
                return;
            }

            // Check content type
            String contentType = connection.getContentType();
            if (!contentType.startsWith("image/png")) {
                connection.disconnect();

                Bundle b = new Bundle();
                b.putString(RET_NAME, modname);
                b.putString(RET_ACTION, ACTION_FETCH_SCREENSHOT);
                b.putString(RET_ERROR, "Failed to download: File is not an image file");
                rec.send(0, b);
                return;
            }

            // download the file
            File file = Utils.getTmpPath(getCacheDir(), ".png");
            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = new FileOutputStream(file);
            byte data[] = new byte[1024];
            int count;
            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
            }
            output.flush();
            output.close();
            input.close();


            Bundle b = new Bundle();
            b.putString(RET_AUTHOR, author);
            b.putString(RET_NAME, modname);
            b.putString(RET_ACTION, ACTION_FETCH_SCREENSHOT);
            b.putString(RET_DEST, file.getAbsolutePath());
            rec.send(0, b);
        } catch (IOException e) {
            Bundle b = new Bundle();
            b.putString(RET_NAME, modname);
            b.putString(RET_ACTION, ACTION_FETCH_SCREENSHOT);
            b.putString(RET_ERROR, "Failed to download: " + e.toString());
            rec.send(0, b);
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void handleActionFetchScreenshot(@NonNull ResultReceiver rec, @NonNull String modname,
                                        @Nullable String author) {
        final String url_str = "http://app-mtmm.rubenwardy.com/screenshot/" + author + "/" + modname + "/";
        doFetchScreenshotUrl(rec, modname, author, url_str, 1);
    }


    /**
     * Handle action Install in the provided background thread with the provided
     * parameters.
     */
    @WorkerThread
    private void handleActionInstall(@NonNull ResultReceiver rec, @NonNull String modname,
                                     @Nullable String author, @NonNull File zipfile,
                                     @NonNull File dest) {
        File dir;
        int i = 0;
        do {
            dir = new File(getCacheDir(), "tmp" + Integer.toString(i));
            i++;
        } while (dir.exists());

        try {
            // UNZIP
            if (!Utils.UnzipFile(zipfile, dir, null)) {
                Log.e("ModService", "Unable to extract zip.");
                Bundle b = new Bundle();
                b.putString(RET_ACTION, ACTION_INSTALL);
                b.putString(RET_NAME, modname);
                b.putString(RET_DEST, dest.getAbsolutePath());
                b.putString(RET_ERROR, "Unable to extract zip. The file may be corrupted.");
                rec.send(0, b);
                return;
            }

            // Find mod_root
            File mod_root = Utils.findRootDir(dir);
            if (mod_root == null) {
                Log.e("ModService", "Unable to find mod_root dir.");
                Bundle b = new Bundle();
                b.putString(RET_ACTION, ACTION_INSTALL);
                b.putString(RET_NAME, modname);
                b.putString(RET_DEST, dest.getAbsolutePath());
                b.putString(RET_ERROR, "Unable to find the mod in the download.");
                rec.send(0, b);
                return;
            }

            // Copy
            File mod_dest = new File(dest, modname);
            Utils.copyFolder(mod_root, mod_dest);

            if (author != null) {
                File file = new File(mod_dest, "author.txt");
                try {
                    FileOutputStream f = new FileOutputStream(file);
                    PrintWriter pw = new PrintWriter(f);
                    pw.println(author);
                    pw.flush();
                    pw.close();
                    f.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Bundle b = new Bundle();
            b.putString(RET_NAME, modname);
            b.putString(RET_ACTION, ACTION_INSTALL);
            b.putString(RET_DEST, dest.getAbsolutePath());
            rec.send(0, b);
        } catch (IOException e) {
            Bundle b = new Bundle();
            b.putString(RET_ACTION, ACTION_INSTALL);
            b.putString(RET_NAME, modname);
            b.putString(RET_DEST, dest.getAbsolutePath());
            b.putString(RET_ERROR, e.toString());
            rec.send(0, b);
            e.printStackTrace();
        }

        if (dir.exists()) {
            Utils.deleteRecursive(dir);
        }
    }

    @WorkerThread
    private void handleActionFetchModList(@NonNull ResultReceiver rec, @NonNull String url_str) {
        try {
            URL url = new URL(url_str);
            URLConnection connection = url.openConnection();
            connection.connect();
            // this will be useful so that you can show a typical 0-100% progress bar
            int fileLength = connection.getContentLength();

            File file;
            int i = 0;
            do {
                file = new File(getCacheDir(), "list" + Integer.toString(i) + ".json");
                i++;
            } while (file.exists());

            // download the file
            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = new FileOutputStream(file);

            byte data[] = new byte[1024];
            int count;
            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            input.close();

            Bundle b = new Bundle();
            b.putString(RET_ACTION, ACTION_FETCH_MODLIST);
            b.putString(RET_NAME, url_str);
            b.putString(RET_DEST, file.getAbsolutePath());
            rec.send(0, b);
        } catch (IOException e) {
            Bundle b = new Bundle();
            b.putString(RET_ACTION, ACTION_FETCH_MODLIST);
            b.putString(RET_NAME, url_str);
            b.putString(RET_ERROR, e.toString());
            rec.send(0, b);
            e.printStackTrace();
        }
    }
}
