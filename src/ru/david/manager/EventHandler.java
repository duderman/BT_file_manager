
package ru.david.manager;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;


public class EventHandler implements OnClickListener {

    private static final int SEARCH_TYPE = 0x00;
    private static final int COPY_TYPE = 0x01;
    private static final int UNZIP_TYPE = 0x02;
    private static final int UNZIPTO_TYPE = 0x03;
    private static final int ZIP_TYPE = 0x04;
    private static final int DELETE_TYPE = 0x05;

    private final Context mContext;
    private final FileManager mFileMang;
    private ThumbnailCreator mThumbnail;
    private TableRow mDelegate;

    private boolean delete_after_copy = false;
    private boolean thumbnail_flag = true;
    private int mColor = Color.WHITE;

    private ArrayList<String> mDataSource;
    private TextView mPathLabel;
    private TextView mInfoLabel;


    public EventHandler(Context context, final FileManager manager) {
        mContext = context;
        mFileMang = manager;

        mDataSource = new ArrayList<String>(mFileMang.setHomeDir
                (Environment.getExternalStorageDirectory().getPath()));
    }

    public EventHandler(Context context, final FileManager manager, String location) {
        mContext = context;
        mFileMang = manager;

        mDataSource = new ArrayList<String>(mFileMang.getNextDir(location, true));
    }

    public void setListAdapter(TableRow adapter) {
        mDelegate = adapter;
    }

    public void setUpdateLabels(TextView path, TextView label) {
        mPathLabel = path;
        mInfoLabel = label;
    }

    public void setShowThumbnails(boolean show) {
        thumbnail_flag = show;
    }

    public void setDeleteAfterCopy(boolean delete) {
        delete_after_copy = delete;
    }

    public void searchForFile(String name) {
        new BackgroundWork(SEARCH_TYPE).execute(name);
    }

    public void deleteFile(String name) {
        new BackgroundWork(DELETE_TYPE).execute(name);
    }

    public void copyFile(String oldLocation, String newLocation) {
        String[] data = {oldLocation, newLocation};

        new BackgroundWork(COPY_TYPE).execute(data);
    }

    public void unZipFile(String file, String path) {
        new BackgroundWork(UNZIP_TYPE).execute(file, path);
    }

    public void unZipFileToDir(String name, String newDir, String oldDir) {
        new BackgroundWork(UNZIPTO_TYPE).execute(name, newDir, oldDir);
    }

    public void zipFile(String zipPath) {
        new BackgroundWork(ZIP_TYPE).execute(zipPath);
    }

    public void stopThumbnailThread() {
        if (mThumbnail != null) {
            mThumbnail.setCancelThumbnails(true);
            mThumbnail = null;
        }
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.back_button:
                if (mFileMang.getCurrentDir() != "/") {
                    stopThumbnailThread();
                    updateDirectory(mFileMang.getPreviousDir());
                    if (mPathLabel != null)
                        mPathLabel.setText(mFileMang.getCurrentDir());
                }
                break;

            case R.id.home_button:
                stopThumbnailThread();
                updateDirectory(mFileMang.setHomeDir("/sdcard"));
                if (mPathLabel != null)
                    mPathLabel.setText(mFileMang.getCurrentDir());
                break;

            case R.id.help_button:
                Intent help = new Intent(mContext, HelpManager.class);
                mContext.startActivity(help);
                break;

            case R.id.blt_button:
                Intent bluetooth = new Intent(mContext, Bluetooth.class);
                mContext.startActivity(bluetooth);
                break;
        }
    }

    public String getData(int position) {

        if (position > mDataSource.size() - 1 || position < 0)
            return null;

        return mDataSource.get(position);
    }

    public void updateDirectory(ArrayList<String> content) {
        if (!mDataSource.isEmpty())
            mDataSource.clear();

        for (String data : content)
            mDataSource.add(data);

        mDelegate.notifyDataSetChanged();
    }

    private static class ViewHolder {
        TextView topView;
        TextView bottomView;
        ImageView icon;
    }


    public class TableRow extends ArrayAdapter<String> {
        private final int KB = 1024;
        private final int MG = KB * KB;
        private final int GB = MG * KB;
        private String display_size;
        private ArrayList<Integer> positions;

        public TableRow() {
            super(mContext, R.layout.tablerow, mDataSource);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder mViewHolder;
            int num_items = 0;
            String temp = mFileMang.getCurrentDir();
            File file = new File(temp + "/" + mDataSource.get(position));
            String[] list = file.list();

            if (list != null)
                num_items = list.length;

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.
                        getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.tablerow, parent, false);

                mViewHolder = new ViewHolder();
                mViewHolder.topView = (TextView) convertView.findViewById(R.id.top_view);
                mViewHolder.bottomView = (TextView) convertView.findViewById(R.id.bottom_view);
                mViewHolder.icon = (ImageView) convertView.findViewById(R.id.row_image);

                convertView.setTag(mViewHolder);

            } else {
                mViewHolder = (ViewHolder) convertView.getTag();
            }

            mViewHolder.topView.setTextColor(mColor);
            mViewHolder.bottomView.setTextColor(mColor);

            if (mThumbnail == null)
                mThumbnail = new ThumbnailCreator(52, 52);

            if (file != null && file.isFile()) {
                String ext = file.toString();
                String sub_ext = ext.substring(ext.lastIndexOf(".") + 1);

    			/* This series of else if statements will determine which 
                 * icon is displayed
    			 */
                if (sub_ext.equalsIgnoreCase("pdf")) {
                    mViewHolder.icon.setImageResource(R.drawable.pdf);

                } else if (sub_ext.equalsIgnoreCase("mp3") ||
                        sub_ext.equalsIgnoreCase("wma") ||
                        sub_ext.equalsIgnoreCase("m4a") ||
                        sub_ext.equalsIgnoreCase("m4p")) {

                    mViewHolder.icon.setImageResource(R.drawable.music);

                } else if (sub_ext.equalsIgnoreCase("png") ||
                        sub_ext.equalsIgnoreCase("jpg") ||
                        sub_ext.equalsIgnoreCase("jpeg") ||
                        sub_ext.equalsIgnoreCase("gif") ||
                        sub_ext.equalsIgnoreCase("tiff")) {

                    if (thumbnail_flag && file.length() != 0) {
                        Bitmap thumb = mThumbnail.isBitmapCached(file.getPath());

                        if (thumb == null) {
                            final Handler handle = new Handler(new Handler.Callback() {
                                public boolean handleMessage(Message msg) {
                                    notifyDataSetChanged();

                                    return true;
                                }
                            });

                            mThumbnail.createNewThumbnail(mDataSource, mFileMang.getCurrentDir(), handle);

                            if (!mThumbnail.isAlive())
                                mThumbnail.start();

                        } else {
                            mViewHolder.icon.setImageBitmap(thumb);
                        }

                    } else {
                        mViewHolder.icon.setImageResource(R.drawable.image);
                    }

                } else if (sub_ext.equalsIgnoreCase("zip") ||
                        sub_ext.equalsIgnoreCase("gzip") ||
                        sub_ext.equalsIgnoreCase("gz")) {

                    mViewHolder.icon.setImageResource(R.drawable.zip);

                } else if (sub_ext.equalsIgnoreCase("m4v") ||
                        sub_ext.equalsIgnoreCase("wmv") ||
                        sub_ext.equalsIgnoreCase("3gp") ||
                        sub_ext.equalsIgnoreCase("mp4")) {

                    mViewHolder.icon.setImageResource(R.drawable.movies);

                } else if (sub_ext.equalsIgnoreCase("doc") ||
                        sub_ext.equalsIgnoreCase("docx")) {

                    mViewHolder.icon.setImageResource(R.drawable.word);

                } else if (sub_ext.equalsIgnoreCase("xls") ||
                        sub_ext.equalsIgnoreCase("xlsx")) {

                    mViewHolder.icon.setImageResource(R.drawable.excel);

                } else if (sub_ext.equalsIgnoreCase("ppt") ||
                        sub_ext.equalsIgnoreCase("pptx")) {

                    mViewHolder.icon.setImageResource(R.drawable.ppt);

                } else if (sub_ext.equalsIgnoreCase("html")) {
                    mViewHolder.icon.setImageResource(R.drawable.html32);

                } else if (sub_ext.equalsIgnoreCase("xml")) {
                    mViewHolder.icon.setImageResource(R.drawable.xml32);

                } else if (sub_ext.equalsIgnoreCase("conf")) {
                    mViewHolder.icon.setImageResource(R.drawable.config32);

                } else if (sub_ext.equalsIgnoreCase("apk")) {
                    mViewHolder.icon.setImageResource(R.drawable.appicon);

                } else if (sub_ext.equalsIgnoreCase("jar")) {
                    mViewHolder.icon.setImageResource(R.drawable.jar32);

                } else {
                    mViewHolder.icon.setImageResource(R.drawable.text);
                }

            } else if (file != null && file.isDirectory()) {
                if (file.canRead() && file.list().length > 0)
                    mViewHolder.icon.setImageResource(R.drawable.folder_full);
                else
                    mViewHolder.icon.setImageResource(R.drawable.folder);
            }

            if (file.isFile()) {
                double size = file.length();
                if (size > GB)
                    display_size = String.format("%.2f Гб ", (double) size / GB);
                else if (size < GB && size > MG)
                    display_size = String.format("%.2f Мб ", (double) size / MG);
                else if (size < MG && size > KB)
                    display_size = String.format("%.2f Кб ", (double) size / KB);
                else
                    display_size = String.format("%.2f байтов ", (double) size);

                if (file.isHidden())
                    mViewHolder.bottomView.setText(display_size);
                else
                    mViewHolder.bottomView.setText(display_size);

            } else {
                mViewHolder.bottomView.setText(num_items + " файл(ов)");
            }

            mViewHolder.topView.setText(file.getName());

            return convertView;
        }

    }

    private class BackgroundWork extends AsyncTask<String, Void, ArrayList<String>> {
        private String file_name;
        private ProgressDialog pr_dialog;
        private int type;
        private int copy_rtn;

        private BackgroundWork(int type) {
            this.type = type;
        }

        /**
         * This is done on the EDT thread. this is called before
         * doInBackground is called
         */
        @Override
        protected void onPreExecute() {

            switch (type) {
                case SEARCH_TYPE:
                    pr_dialog = ProgressDialog.show(mContext, "Searching",
                            "Поиск файла...",
                            true, true);
                    break;

                case COPY_TYPE:
                    pr_dialog = ProgressDialog.show(mContext, "Copying",
                            "Копирование файла...",
                            true, false);
                    break;

                case UNZIP_TYPE:
                    pr_dialog = ProgressDialog.show(mContext, "Unzipping",
                            "Распаковка zip файла, пожалуйта подождите...",
                            true, false);
                    break;

                case UNZIPTO_TYPE:
                    pr_dialog = ProgressDialog.show(mContext, "Unzipping",
                            "Распаковка zip файла, пожалуйта подождите...",
                            true, false);
                    break;

                case ZIP_TYPE:
                    pr_dialog = ProgressDialog.show(mContext, "Zipping",
                            "Аривирование папки...",
                            true, false);
                    break;

                case DELETE_TYPE:
                    pr_dialog = ProgressDialog.show(mContext, "Deleting",
                            "Удаление фалов...",
                            true, false);
                    break;
            }
        }

        @Override
        protected ArrayList<String> doInBackground(String... params) {

            switch (type) {
                case SEARCH_TYPE:
                    file_name = params[0];
                    ArrayList<String> found = mFileMang.searchInDirectory(mFileMang.getCurrentDir(),
                            file_name);
                    return found;

                case COPY_TYPE:
                    int len = params.length;
                    delete_after_copy = false;
                    return null;

                case UNZIP_TYPE:
                    mFileMang.extractZipFiles(params[0], params[1]);
                    return null;

                case UNZIPTO_TYPE:
                    mFileMang.extractZipFilesFromDir(params[0], params[1], params[2]);
                    return null;

                case ZIP_TYPE:
                    mFileMang.createZipFile(params[0]);
                    return null;

                case DELETE_TYPE:
                    int size = params.length;

                    for (int i = 0; i < size; i++)
                        mFileMang.deleteTarget(params[i]);

                    return null;
            }
            return null;
        }

        @Override
        protected void onPostExecute(final ArrayList<String> file) {
            final CharSequence[] names;
            int len = file != null ? file.size() : 0;

            switch (type) {
                case SEARCH_TYPE:
                    if (len == 0) {
                        Toast.makeText(mContext, "Couldn't find " + file_name,
                                Toast.LENGTH_SHORT).show();

                    } else {
                        names = new CharSequence[len];

                        for (int i = 0; i < len; i++) {
                            String entry = file.get(i);
                            names[i] = entry.substring(entry.lastIndexOf("/") + 1, entry.length());
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                        builder.setTitle("Найдено " + len + " файл(ов)");
                        builder.setItems(names, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int position) {
                                String path = file.get(position);
                                updateDirectory(mFileMang.getNextDir(path.
                                        substring(0, path.lastIndexOf("/")), true));
                            }
                        });

                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }

                    pr_dialog.dismiss();
                    break;

                case COPY_TYPE:
                    if (copy_rtn == 0)
                        Toast.makeText(mContext, "Файл удачно скопирован и вставлен",
                                Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(mContext, "Копирование не удалось", Toast.LENGTH_SHORT).show();

                    pr_dialog.dismiss();
                    mInfoLabel.setText("");
                    break;

                case UNZIP_TYPE:
                    updateDirectory(mFileMang.getNextDir(mFileMang.getCurrentDir(), true));
                    pr_dialog.dismiss();
                    break;

                case UNZIPTO_TYPE:
                    updateDirectory(mFileMang.getNextDir(mFileMang.getCurrentDir(), true));
                    pr_dialog.dismiss();
                    break;

                case ZIP_TYPE:
                    updateDirectory(mFileMang.getNextDir(mFileMang.getCurrentDir(), true));
                    pr_dialog.dismiss();
                    break;

                case DELETE_TYPE:

                    updateDirectory(mFileMang.getNextDir(mFileMang.getCurrentDir(), true));
                    pr_dialog.dismiss();
                    mInfoLabel.setText("");
                    break;
            }
        }
    }
}
