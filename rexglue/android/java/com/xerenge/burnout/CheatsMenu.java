package com.xerenge.burnout;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The in-game menu behind the button at the top right: cheats, the layout
 * editor and restarting the game. Cheats take effect on the next start, so
 * saving them offers the restart.
 */
final class CheatsMenu {
    interface Host {
        void editLayout();
        boolean canEditLayout();
    }

    private final Activity activity;
    private final Host host;
    private Cheats cheats;

    CheatsMenu(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    void show() {
        java.util.List<String> items = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();
        items.add("Cheats");
        actions.add(this::showCheats);
        if (host.canEditLayout()) {
            items.add("Edit layout");
            actions.add(host::editLayout);
        }
        items.add("Back up saves");
        actions.add(() -> SaveBackup.backup(activity));
        items.add("Restore saves");
        actions.add(() -> SaveBackup.restore(activity));
        items.add("Restart the game");
        actions.add(() -> restart(activity));
        new AlertDialog.Builder(activity)
                .setTitle("Menu")
                .setItems(items.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton("Close", null)
                .show();
    }

    private void showCheats() {
        cheats = Cheats.load(activity);
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        page.setPadding(pad, pad / 2, pad, 0);
        final boolean[] changed = {false};
        if (cheats.list.isEmpty()) {
            TextView none = new TextView(activity);
            none.setText("No cheats yet. Add one with its address in the game's memory (this is the Europe disc) and the value to hold there.");
            page.addView(none);
        }
        for (Cheats.Cheat cheat : cheats.list) {
            CheckBox box = new CheckBox(activity);
            box.setText(cheat.name + "  (" + String.format("%08X", cheat.address) + ")");
            box.setChecked(cheat.enabled);
            box.setOnCheckedChangeListener((b, on) -> {
                cheat.enabled = on;
                changed[0] = true;
            });
            box.setOnLongClickListener(v -> {
                new AlertDialog.Builder(activity)
                        .setMessage("Remove \"" + cheat.name + "\"?")
                        .setPositiveButton("Remove", (d, w) -> {
                            cheats.list.remove(cheat);
                            page.removeView(box);
                            changed[0] = true;
                        })
                        .setNegativeButton("Keep", null)
                        .show();
                return true;
            });
            page.addView(box);
        }
        TextView hint = new TextView(activity);
        hint.setText("Long press a cheat to remove it. Changes apply when the game restarts.");
        hint.setTextSize(12);
        hint.setPadding(0, pad / 2, 0, 0);
        page.addView(hint);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(page);
        new AlertDialog.Builder(activity)
                .setTitle("Cheats")
                .setView(scroll)
                .setPositiveButton("Save and restart", (d, w) -> saveAndRestart())
                .setNeutralButton("Add a cheat", (d, w) -> {
                    cheats.save(activity);  // keeps what was ticked so far
                    addCheat();
                })
                .setNegativeButton("Save", (d, w) -> {
                    if (cheats.save(activity)) {
                        Toast.makeText(activity, "Saved. Restart the game to apply.", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void saveAndRestart() {
        if (!cheats.save(activity)) {
            Toast.makeText(activity, "Could not save the cheats", Toast.LENGTH_LONG).show();
            return;
        }
        restart(activity);
    }

    private EditText field(LinearLayout page, String label, String hint, int type) {
        TextView title = new TextView(activity);
        title.setText(label);
        page.addView(title);
        EditText edit = new EditText(activity);
        edit.setHint(hint);
        edit.setInputType(type);
        edit.setSingleLine(true);
        page.addView(edit);
        return edit;
    }

    private void addCheat() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        page.setPadding(pad, pad / 2, pad, 0);
        EditText name = field(page, "Name", "Unlimited boost", InputType.TYPE_CLASS_TEXT);
        EditText address = field(page, "Address (hex)", "82A5D3F0", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        EditText value = field(page, "Value (hex)", "3F800000", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        TextView sizeTitle = new TextView(activity);
        sizeTitle.setText("Size");
        page.addView(sizeTitle);
        Spinner size = new Spinner(activity);
        size.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[] {"4 bytes", "2 bytes", "1 byte"}));
        page.addView(size);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(page);
        new AlertDialog.Builder(activity)
                .setTitle("Add a cheat")
                .setView(scroll)
                .setPositiveButton("Add", (d, w) -> {
                    int bytes = new int[] {4, 2, 1}[size.getSelectedItemPosition()];
                    try {
                        long a = Long.parseLong(address.getText().toString().trim().replace("0x", ""), 16);
                        long v = Long.parseLong(value.getText().toString().trim().replace("0x", ""), 16);
                        String n = name.getText().toString().trim();
                        if (a < 0x10000 || a > 0xFFFFFFFFL || v < 0 || v >= (1L << (8 * bytes))) {
                            throw new NumberFormatException();
                        }
                        cheats = Cheats.load(activity);
                        cheats.list.add(new Cheats.Cheat(n.isEmpty() ? "Cheat" : n, a, bytes, v, true));
                        cheats.save(activity);
                    } catch (NumberFormatException e) {
                        Toast.makeText(activity, "That address or value is not valid hex for the size", Toast.LENGTH_LONG).show();
                    }
                    showCheats();
                })
                .setNegativeButton("Cancel", (d, w) -> showCheats())
                .show();
    }

    /** The game over, from a process of its own: see RestartActivity. */
    static void restart(Context context) {
        Intent intent = new Intent(context, RestartActivity.class);
        intent.putExtra(RestartActivity.EXTRA_PID, android.os.Process.myPid());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
