package com.rubenwardy.minetestmodmanager.manager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A list of mods. May represent a folder in the file system or
 */
public class ModList {
    public enum ModListType {
        EMLT_ONLINE,
        EMLT_PATH
    }

    @Nullable public final String title;
    @NonNull  public final String listname;
    @Nullable public final String engine_root;
    @NonNull public final ModListType type;
    public boolean valid;
    @NonNull
    public List<Mod> mods = new ArrayList<>();
    @NonNull
    public Map<String, List<Mod>> mods_map = new HashMap<>();

    public ModList(@NonNull ModListType type, @Nullable String title, @Nullable String engine_root,
            @NonNull String listname) {
        this.type = type;
        this.title = title;
        this.engine_root = engine_root;
        this.listname = listname;
        this.valid = true;
    }

    public void add(@NonNull Mod mod) {
        mods.add(mod);
        if (mods_map.containsKey(mod.name)) {
            List<Mod> tmp = mods_map.get(mod.name);
            tmp.add(mod);
        } else {
            List<Mod> tmp = new ArrayList<>();
            tmp.add(mod);
            mods_map.put(mod.name, tmp);
        }
    }

    @Nullable
    public String getWorldsDir() {
        if (engine_root == null) {
            return null;
        } else {
            return (new File(engine_root, "worlds")).getAbsolutePath();
        }
    }

    @Nullable
    public Mod get(@Nullable String name, @Nullable String author) {
        if (name == null) {
            return null;
        }
        if (author != null) {
            author = author.trim();
            if (author.equals("")) {
                author = null;
            }
        }

        List<Mod> res = mods_map.get(name);
        if (res != null && res.size() > 0) {
            if (author == null) {
                if (res.size() > 1) {
                    Log.e("ModList",
                            "get() called without author, yet there are multiple mods of that name.");
                }
                return res.get(0);
            }
            for (Mod mod : res) {
                if (mod.author.equals(author)) {
                    return mod;
                }
            }
        }
        return null;
    }
}
