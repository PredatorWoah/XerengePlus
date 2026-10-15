package com.xerenge.burnout;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backs the saves and settings up to a zip the player picks the place of (the
 * system file picker, so Downloads or a cloud drive: somewhere an uninstall does
 * not reach), and puts them back from one. What goes in: user/ (the saves) and
 * xerenge.conf, cheats.conf and touch_layout.conf. The extracted disc, game/, is
 * left out: it is large, and the installer makes it again from the disc image.
 */
final class SaveBackup {
    static final int REQUEST_BACKUP = 0x5B01;
    static final int REQUEST_RESTORE = 0x5B02;

    private static final String[] FILES = {"xerenge.conf", "cheats.conf", "touch_layout.conf"};
    private static final String USER_DIR = "user";

    private SaveBackup() {}

    static void backup(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "xerenge-saves-"
                + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".zip");
        activity.startActivityForResult(intent, REQUEST_BACKUP);
    }

    static void restore(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Restore saves")
                .setMessage("Pick a backup zip. Its saves and settings replace the ones now on this "
                        + "device, and the game restarts.")
                .setPositiveButton("Pick a backup", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    activity.startActivityForResult(intent, REQUEST_RESTORE);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** From the activity's onActivityResult; true when the result was ours. */
    static boolean onResult(Activity activity, int request, int result, Intent data) {
        if (request != REQUEST_BACKUP && request != REQUEST_RESTORE) {
            return false;
        }
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        final Uri uri = data.getData();
        final boolean saving = request == REQUEST_BACKUP;
        new Thread(() -> {
            String message;
            try {
                message = saving ? write(activity, uri) : read(activity, uri);
            } catch (Exception error) {
                message = (saving ? "Backup failed: " : "Restore failed: ") + error.getMessage();
            }
            final String shown = message;
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, shown, Toast.LENGTH_LONG).show();
                if (!saving && shown.startsWith("Restored")) {
                    CheatsMenu.restart(activity);
                }
            });
        }, "saves").start();
        return true;
    }

    private static String write(Activity activity, Uri uri) throws Exception {
        File root = Settings.root(activity);
        int count = 0;
        try (OutputStream raw = activity.getContentResolver().openOutputStream(uri);
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            for (String name : FILES) {
                File file = new File(root, name);
                if (file.isFile()) {
                    add(zip, file, name);
                    ++count;
                }
            }
            count += addTree(zip, new File(root, USER_DIR), USER_DIR);
        }
        if (count == 0) {
            return "Nothing to back up yet";
        }
        return "Backed up " + count + " files";
    }

    private static int addTree(ZipOutputStream zip, File dir, String path) throws Exception {
        File[] children = dir.listFiles();
        if (children == null) {
            return 0;
        }
        int count = 0;
        for (File child : children) {
            String name = path + "/" + child.getName();
            if (child.isDirectory()) {
                count += addTree(zip, child, name);
            } else {
                add(zip, child, name);
                ++count;
            }
        }
        return count;
    }

    private static void add(ZipOutputStream zip, File file, String name) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                zip.write(buffer, 0, n);
            }
        }
        zip.closeEntry();
    }

    private static String read(Activity activity, Uri uri) throws Exception {
        File root = Settings.root(activity);
        String rootPath = root.getCanonicalPath() + File.separator;
        int count = 0;
        try (InputStream raw = activity.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                boolean wanted = !entry.isDirectory() && (name.startsWith(USER_DIR + "/") || isListed(name));
                if (!wanted) {
                    continue;
                }
                File target = new File(root, name);
                // Nothing may land outside the app's folder.
                if (!target.getCanonicalPath().startsWith(rootPath)) {
                    continue;
                }
                target.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = zip.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                }
                ++count;
            }
        }
        if (count == 0) {
            return "That zip holds no saves";
        }
        return "Restored " + count + " files";
    }

    private static boolean isListed(String name) {
        for (String file : FILES) {
            if (file.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
