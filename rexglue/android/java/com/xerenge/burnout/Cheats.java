package com.xerenge.burnout;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The cheats of the PAL (Europe) disc: memory patches the game applies every
 * frame (src/cheats.cpp). They are kept in cheats.conf beside xerenge.conf, a
 * line each:
 *
 *   name | address | size | value        enabled
 *   #- name | address | size | value     kept, switched off
 *
 * The native side skips every line starting with '#', so a switched-off cheat
 * costs nothing. Address and value are hex, size is 1, 2 or 4 bytes.
 */
final class Cheats {
    static final class Cheat {
        String name;
        long address;
        int size;
        long value;
        boolean enabled;

        Cheat(String name, long address, int size, long value, boolean enabled) {
            this.name = name;
            this.address = address;
            this.size = size;
            this.value = value;
            this.enabled = enabled;
        }

        String line() {
            return (enabled ? "" : "#- ") + name.replace('|', '/').replace('\n', ' ') + " | "
                    + String.format(Locale.US, "%08X", address) + " | " + size + " | "
                    + String.format(Locale.US, "%0" + (size * 2) + "X", value);
        }
    }

    /**
     * Cheats offered ready-made, as {name, address, size, value}. Only an address
     * checked against this disc belongs here; the menu's "Add a cheat" takes the
     * rest.
     */
    static final Object[][] CATALOG = {};

    final List<Cheat> list = new ArrayList<>();

    static File file(Context context) {
        return new File(Settings.root(context), "cheats.conf");
    }

    static Cheats load(Context context) {
        Cheats cheats = new Cheats();
        for (Object[] entry : CATALOG) {
            cheats.list.add(new Cheat((String) entry[0], ((Number) entry[1]).longValue(),
                    ((Number) entry[2]).intValue(), ((Number) entry[3]).longValue(), false));
        }
        File file = file(context);
        if (!file.isFile()) {
            return cheats;
        }
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                boolean enabled = true;
                if (line.startsWith("#-")) {
                    enabled = false;
                    line = line.substring(2).trim();
                } else if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\\|");
                if (f.length != 4) {
                    continue;
                }
                try {
                    Cheat cheat = new Cheat(f[0].trim(), Long.parseLong(f[1].trim(), 16),
                            Integer.parseInt(f[2].trim()), Long.parseLong(f[3].trim(), 16), enabled);
                    cheats.merge(cheat);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return cheats;
    }

    /** A saved cheat takes over the catalog entry it came from; anything else is the user's own. */
    private void merge(Cheat cheat) {
        for (Cheat known : list) {
            if (known.address == cheat.address && known.size == cheat.size && known.name.equals(cheat.name)) {
                known.value = cheat.value;
                known.enabled = cheat.enabled;
                return;
            }
        }
        list.add(cheat);
    }

    boolean save(Context context) {
        StringBuilder out = new StringBuilder("# Burnout Revenge cheats: name | address | size | value\n");
        for (Cheat cheat : list) {
            out.append(cheat.line()).append('\n');
        }
        File file = file(context);
        file.getParentFile().mkdirs();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(out.toString());
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    boolean anyEnabled() {
        for (Cheat cheat : list) {
            if (cheat.enabled) {
                return true;
            }
        }
        return false;
    }
}
