package com.rubenwardy.minetestmodmanager.manager;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.ResultReceiver;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * Receive a result from the service
 */
@SuppressLint("ParcelCreator")
public class ServiceResultReceiver extends ResultReceiver {
    public ServiceResultReceiver(Handler handler) {
        super(handler);
    }

    private void handleInstall(@NonNull Bundle b, @Nullable String modname, @Nullable String dest) {
        if (b.containsKey(ModInstallService.RET_ERROR)) {

            if (ModManager.mev != null) {
                final String error = b.getString(ModInstallService.RET_ERROR);

                Bundle b2 = new Bundle();
                b2.putString(ModEventReceiver.PARAM_ACTION, ModEventReceiver.ACTION_INSTALL);
                b2.putString(ModEventReceiver.PARAM_MODNAME, modname);
                b2.putString(ModEventReceiver.PARAM_DEST_LIST, dest);
                b2.putString(ModEventReceiver.PARAM_ERROR, error);
                //noinspection ConstantConditions
                ModManager.mev.onModEvent(b2);
            }
        } else {
            ModManager modman = new ModManager();
            ModList list = modman.get(dest);
            if (list != null) {
                list.valid = false;
            }

            if (ModManager.mev != null) {
                Bundle b2 = new Bundle();
                b2.putString(ModEventReceiver.PARAM_ACTION, ModEventReceiver.ACTION_INSTALL);
                b2.putString(ModEventReceiver.PARAM_MODNAME, modname);
                b2.putString(ModEventReceiver.PARAM_DEST_LIST, dest);
                //noinspection ConstantConditions
                ModManager.mev.onModEvent(b2);
            }
        }
    }

    private void handleUninstall(@NonNull Bundle b, @Nullable String modname, @Nullable String dest) {
        if (b.containsKey(ModInstallService.RET_ERROR)) {
            if (ModManager.mev != null) {
                final String error = b.getString(ModInstallService.RET_ERROR);

                Bundle b2 = new Bundle();
                b2.putString(ModEventReceiver.PARAM_ACTION, ModEventReceiver.ACTION_UNINSTALL);
                b2.putString(ModEventReceiver.PARAM_MODNAME, modname);
                b2.putString(ModEventReceiver.PARAM_DEST_LIST, dest);
                b2.putString(ModEventReceiver.PARAM_ERROR, error);
                //noinspection ConstantConditions
                ModManager.mev.onModEvent(b2);
            }
        } else {
            ModManager modman = new ModManager();
            ModList list = modman.get(dest);
            if (list != null) {
                list.valid = false;
            }

            if (ModManager.mev != null) {
                Bundle b2 = new Bundle();
                b2.putString(ModEventReceiver.PARAM_ACTION, ModEventReceiver.ACTION_UNINSTALL);
                b2.putString(ModEventReceiver.PARAM_MODNAME, modname);
                b2.putString(ModEventReceiver.PARAM_DEST_LIST, dest);
                //noinspection ConstantConditions
                ModManager.mev.onModEvent(b2);
            }
        }
    }

    private void handleFetchModList(@NonNull Bundle b, @Nullable String url, @Nullable String dest) {
        if (url == null) {
            return;
        }

        if (dest == null || b.containsKey(ModInstallService.RET_ERROR)) {
            if (ModManager.mev != null) {
                Bundle b2 = new Bundle();
                b2.putString(ModEventReceiver.PARAM_ACTION, ModEventReceiver.ACTION_FETCH_MODLIST);
                b2.putString(ModEventReceiver.PARAM_DEST_LIST, url);
                b2.putString(ModEventReceiver.PARAM_ERROR, b.getString(ModInstallService.RET_ERROR));
                //noinspection ConstantConditions
                ModManager.mev.onModEvent(b2);
            }
        } else {
            ModManager modman = new ModManager();
            ModList list = new ModList(ModList.ModListType.EMLT_ONLINE, "Available Mods", null, url);
            list.valid = false;

            try {
                File file = new File(dest);
                if (!file.isFile()) {
                    return;
                }
                JSONArray j = new JSONArray(Utils.readTextFile(file));
                if (!file.delete()) {
                    Log.e("SRR", "Failed to delete file!");
                    return;
                }

                for (int i = 0; i < j.length(); i++) {
                    try {
                        JSONObject item = j.getJSONObject(i);
                        String modname = item.getString("name");
                        String title = item.getString("title");
                        String link = item.getString("link");

                        if (modname != null && title != null && link != null) {
                            String author = item.getString("author");
                            String type_s = item.getString("type");

                            String desc = "";
                            if (item.has("description")) {
                                desc = item.getString("description");
                            }

                            String forum = null;
                            if (item.has("topicId")) {
                                forum = "https://forum.minetest.net/viewtopic.php?t=" +
                                        item.getString("topicId");
                            }

                            int verified = 0;
                            if (item.has("verified")) {
                                verified = item.getInt("verified");
                            }

                            int size = -1;
                            if (item.has("size")) {
                                size = item.getInt("size");
                            }

                            Mod.ModType type = Mod.ModType.EMT_MOD;
                            if (type_s != null) {
                                if (type_s.equals("1")) {
                                    type = Mod.ModType.EMT_MOD;
                                } else if (type_s.equals("2")) {
                                    type = Mod.ModType.EMT_MODPACK;
                                }
                            }

                            Mod mod = new Mod(type, url, modname, title, desc);
                            mod.link = link;
                            mod.author = author;
                            mod.verified = verified;
                            mod.forum_url = forum;
                            mod.size = size;
                            list.add(mod);
                        } else {
                            Log.e("SRR", "Invalid object in JSON list. " + j.toString());
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            modman.addList(list);

            if (ModManager.mev != null) {
                Bundle b2 = new Bundle();
                b2.putString(ModEventReceiver.PARAM_ACTION, ModEventReceiver.ACTION_FETCH_MODLIST);
                b2.putString(ModEventReceiver.PARAM_DEST_LIST, url);
                //noinspection ConstantConditions
                ModManager.mev.onModEvent(b2);
            }
        }
    }

    @Override
    protected void onReceiveResult(int resultCode, @NonNull Bundle b) {
        String modname = b.getString(ModInstallService.RET_NAME);
        String dest = b.getString(ModInstallService.RET_DEST);
        String action = b.getString(ModInstallService.RET_ACTION);
        if (action == null) {
            Log.e("SRR", "Invalid null action");
            return;
        }

        switch (action) {
        case ModInstallService.ACTION_INSTALL:
            handleInstall(b, modname, dest);
            break;
        case ModInstallService.ACTION_UNINSTALL:
            handleUninstall(b, modname, dest);
            break;
        case ModInstallService.ACTION_FETCH_MODLIST:
            handleFetchModList(b, modname, dest);
            break;
        case ModInstallService.ACTION_FETCH_SCREENSHOT:
            if (ModManager.mev != null) {
                Bundle b2 = new Bundle();
                b2.putString(ModEventReceiver.PARAM_ACTION, ModEventReceiver.ACTION_FETCH_SCREENSHOT);
                b2.putString(ModEventReceiver.PARAM_MODNAME, modname);
                b2.putString(ModEventReceiver.PARAM_DEST, dest);
                if (b.containsKey(ModInstallService.RET_ERROR)) {
                    b2.putString(ModEventReceiver.PARAM_ERROR, b.getString(ModInstallService.RET_ERROR));
                }
                //noinspection ConstantConditions
                ModManager.mev.onModEvent(b2);
            }
            break;
        default:
            Log.e("SRR", "Unknown service action");
            break;
        }
    }

}