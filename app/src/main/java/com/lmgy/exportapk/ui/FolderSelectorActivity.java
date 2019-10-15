package com.lmgy.exportapk.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.lmgy.exportapk.Global;
import com.lmgy.exportapk.R;
import com.lmgy.exportapk.base.BaseActivity;
import com.lmgy.exportapk.config.Constant;
import com.lmgy.exportapk.utils.EnvironmentUtils;
import com.lmgy.exportapk.utils.PinyinUtils;
import com.lmgy.exportapk.utils.Storage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author lm
 * @date 2019/10/13
 */
public class FolderSelectorActivity extends BaseActivity {

    private SharedPreferences settings;
    private File file;
    private final Bundle positions = new Bundle();
    private ListView listView;
    private ProgressBar progressBar;
    private ViewGroup attention;
    private TextView textView;
    private String currentStoragePath;

    private final List<String> storages = Storage.INSTANCE.getAvailableStoragePaths();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_selector);

        setSupportActionBar(findViewById(R.id.toolbar));
        listView = findViewById(R.id.folder_selector_listview);
        progressBar = findViewById(R.id.folder_selector_loading);
        attention = findViewById(R.id.folder_selector_att);
        textView = findViewById(R.id.folder_selector_current_path);
        listView.setDivider(null);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        settings = Global.getGlobalSharedPreferences(this);
        file = new File(settings.getString(Constant.INSTANCE.getPREFERENCE_SAVE_PATH(), Constant.INSTANCE.getPREFERENCE_SAVE_PATH_DEFAULT()));
        currentStoragePath = getStoragePathOfFile(file);

        try {
            if (file.exists() && !file.isDirectory()) {
                file.delete();
            }
            if (!file.exists()) {
                file.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
            ToastManager.showToast(this, getResources().getString(R.string.toast_mkdirs_error) + "\n" + e.toString(), Toast.LENGTH_SHORT);
        }

        refreshList(file);
    }


    private void refreshList(@Nullable final File file) {

        listView.setAdapter(null);
        progressBar.setVisibility(View.VISIBLE);
        this.file = file;
        if (file == null || file.getAbsolutePath().length() < currentStoragePath.length()) {
            FolderSelectorActivity.this.file = null;
        }
        new Thread(() -> {
            final ArrayList<FileItem> arrayList = new ArrayList<>();
            synchronized (FolderSelectorActivity.this) {
                try {
                    if (file == null || file.getAbsolutePath().length() < currentStoragePath.length()) {
                        for (String s : storages) {
                            arrayList.add(new FileItem(new File(s)));
                        }
                    } else {
                        final File[] files = file.listFiles();
                        for (File file1 : files) {
                            if (file1.isDirectory() && !file1.isHidden()) {
                                arrayList.add(new FileItem(file1));
                            }
                        }
                    }
                    Collections.sort(arrayList);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Global.HANDLER.post(() -> {
                progressBar.setVisibility(View.GONE);
                BasicListAdapter adapter = new BasicListAdapter(arrayList);
                listView.setAdapter(adapter);
                listView.setOnItemClickListener(adapter);
                if (FolderSelectorActivity.this.file != null) {
                    listView.setSelection(positions.getInt(getFormateLowercaseString(FolderSelectorActivity.this.file.getAbsolutePath())));
                }

                textView.setText(FolderSelectorActivity.this.file == null ?
                        getResources().getString(R.string.activity_folder_selector_select_storage)
                        : FolderSelectorActivity.this.file.getAbsolutePath());
            });

        }).start();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_folder_selector, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            default:
                break;
            case android.R.id.home: {
                backToParentOrExit();
            }
            break;
            case R.id.action_confirm: {
                if (file == null) {
                    ToastManager.showToast(this, getResources().getString(R.string.activity_attention_select_folder), Toast.LENGTH_SHORT);
                    return false;
                }
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(Constant.INSTANCE.getPREFERENCE_SAVE_PATH(), file.getAbsolutePath());
                editor.apply();
                setResult(RESULT_OK);
                ToastManager.showToast(this, getResources().getString(R.string.activity_attention_path_set) + file.getAbsolutePath(), Toast.LENGTH_SHORT);
                finish();
            }
            break;
            case R.id.action_cancel: {
                setResult(RESULT_CANCELED);
                finish();
            }
            break;
            case R.id.action_new_folder: {
                View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_edittext, null);
                final EditText editText = dialogView.findViewById(R.id.dialog_edit_text);

                final AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(getResources().getString(R.string.action_new_folder))
                        .setView(dialogView)
                        .setPositiveButton(getResources().getString(R.string.dialog_button_confirm), null)
                        .setNegativeButton(getResources().getString(R.string.dialog_button_cancel), (dialog1, which) -> {
                        })
                        .show();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String s = editText.getText().toString().trim();
                    if (!EnvironmentUtils.isALegalFileName(s)) {
                        ToastManager.showToast(FolderSelectorActivity.this, getResources().getString(R.string.file_invalid_name), Toast.LENGTH_SHORT);
                        return;
                    }
                    if (s.length() == 0) {
                        ToastManager.showToast(FolderSelectorActivity.this, getResources().getString(R.string.file_blank_name), Toast.LENGTH_SHORT);
                        return;
                    }
                    try {
                        File file = new File(FolderSelectorActivity.this.file.getAbsolutePath() + File.separator + s);
                        file.mkdir();
                        refreshList(FolderSelectorActivity.this.file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    dialog.cancel();
                });
            }
            break;
            case R.id.action_name_ascend: {
                FileItem.config = 0;
                refreshList(file);
            }
            break;
            case R.id.action_name_descend: {
                FileItem.config = 1;
                refreshList(file);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backToParentOrExit();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void backToParentOrExit() {
        try {
            File parentFile = file.getParentFile();
            if (file == null || file.getAbsolutePath().length() < currentStoragePath.length()) {
                finish();
                return;
            }
            refreshList(parentFile);
        } catch (Exception e) {
            e.printStackTrace();
            finish();
        }
    }

    private String getStoragePathOfFile(File file) {
        try {
            for (String s : storages) {
                if (getFormateLowercaseString(file.getAbsolutePath()).startsWith(getFormateLowercaseString(s))) {
                    Log.e("StoragePath", s);
                    return s;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getFormateLowercaseString(String s) {
        return s.trim().toLowerCase();
    }

    private class BasicListAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

        private List<FileItem> list;

        private BasicListAdapter(@NonNull List<FileItem> list) {
            this.list = list;
            if (list.size() == 0) {
                attention.setVisibility(View.VISIBLE);
            } else {
                attention.setVisibility(View.GONE);
            }
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(FolderSelectorActivity.this).inflate(R.layout.item_folder, parent, false);
                holder = new ViewHolder();
                holder.imageView = convertView.findViewById(R.id.item_folder_icon);
                holder.textView = convertView.findViewById(R.id.item_folder_name);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            FileItem item = list.get(position);

            if (FolderSelectorActivity.this.file == null) {
                holder.imageView.setImageResource(R.drawable.icon_sd);
            } else {
                holder.imageView.setImageResource(R.drawable.icon_folder);
            }

            holder.textView.setText(item.file.getName());
            return convertView;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            FileItem item = list.get(position);
            if (FolderSelectorActivity.this.file == null) {
                currentStoragePath = item.file.getAbsolutePath();
            } else {
                positions.putInt(getFormateLowercaseString(FolderSelectorActivity.this.file.getAbsolutePath()), listView.getFirstVisiblePosition());
            }
            refreshList(item.file);
        }
    }


    private static class ViewHolder {
        TextView textView;
        ImageView imageView;
    }

    private static class FileItem implements Comparable<FileItem> {
        File file;
        String name;
        static int config = 0;

        private FileItem(@NonNull File file) {
            this.file = file;
            this.name = file.getName();
        }

        @Override
        public int compareTo(@NonNull FileItem o) {
            switch (config) {
                default:
                    break;
                case 0: {
                    try {
                        if (PinyinUtils.INSTANCE.getFirstSpell(this.name).toLowerCase().compareTo(PinyinUtils.INSTANCE.getFirstSpell(o.name).toLowerCase()) > 0) {
                            return 1;
                        }
                        if (PinyinUtils.INSTANCE.getFirstSpell(this.name).toLowerCase().compareTo(PinyinUtils.INSTANCE.getFirstSpell(o.name).toLowerCase()) < 0) {
                            return -1;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
                case 1: {
                    try {
                        if (PinyinUtils.INSTANCE.getFirstSpell(this.name).toLowerCase().compareTo(PinyinUtils.INSTANCE.getFirstSpell(o.name).toLowerCase()) > 0) {
                            return -1;
                        }
                        if (PinyinUtils.INSTANCE.getFirstSpell(this.name).toLowerCase().compareTo(PinyinUtils.INSTANCE.getFirstSpell(o.name).toLowerCase()) < 0) {
                            return 1;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            return 0;
        }
    }
}
