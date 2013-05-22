package ru.david.manager;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.*;

import java.io.File;


public final class Main extends ListActivity {
    public static final String ACTION_WIDGET = "ru.david.manager.Main.ACTION_WIDGET";

    private static final int MENU_MKDIR = 0x00;            //option menu id
    private static final int MENU_SEARCH = 0x02;            //option menu id
    private static final int MENU_QUIT = 0x04;            //option menu id

    private static final int D_MENU_DELETE = 0x05;            //context menu id
    private static final int D_MENU_RENAME = 0x06;            //context menu id
    private static final int D_MENU_COPY = 0x07;            //context menu id
    private static final int D_MENU_PASTE = 0x08;            //context menu id
    private static final int D_MENU_ZIP = 0x0e;            //context menu id
    private static final int D_MENU_UNZIP = 0x0f;            //context menu id
    private static final int D_MENU_MOVE = 0x30;            //context menu id
    private static final int F_MENU_MOVE = 0x20;            //context menu id
    private static final int F_MENU_DELETE = 0x0a;            //context menu id
    private static final int F_MENU_RENAME = 0x0b;            //context menu id
    private static final int F_MENU_COPY = 0x0d;            //context menu id

    private FileManager mFileMag;
    private EventHandler mHandler;
    private EventHandler.TableRow mTable;

    private boolean mReturnIntent = false;
    private boolean mHoldingFile = false;
    private boolean mHoldingZip = false;
    private boolean mUseBackKey = true;
    private String mCopiedTarget;
    private String mZippedTarget;
    private String mSelectedListItem;                //item from context menu
    private TextView mPathLabel, mDetailLabel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);

        mFileMag = new FileManager();

        if (savedInstanceState != null)
            mHandler = new EventHandler(Main.this, mFileMag, savedInstanceState.getString("location"));
        else
            mHandler = new EventHandler(Main.this, mFileMag);

        mTable = mHandler.new TableRow();
        
        /*sets the ListAdapter for our ListActivity and
         *gives our EventHandler class the same adapter
         */
        mHandler.setListAdapter(mTable);
        setListAdapter(mTable);
        
        /* register context menu for our list view */
        registerForContextMenu(getListView());

        mDetailLabel = (TextView) findViewById(R.id.detail_label);
        mPathLabel = (TextView) findViewById(R.id.path_label);
        mPathLabel.setText("/sdcard");

        mHandler.setUpdateLabels(mPathLabel, mDetailLabel);
        
        /* setup buttons */
        int[] img_button_id = {R.id.help_button, R.id.home_button,
                R.id.back_button, R.id.blt_button};

        int[] button_id = {R.id.hidden_copy, R.id.hidden_attach,
                R.id.hidden_delete, R.id.hidden_move};

        ImageButton[] bimg = new ImageButton[img_button_id.length];
        Button[] bt = new Button[button_id.length];

        for (int i = 0; i < img_button_id.length; i++) {
            bimg[i] = (ImageButton) findViewById(img_button_id[i]);
            bimg[i].setOnClickListener(mHandler);

            if (i < 4) {
                bt[i] = (Button) findViewById(button_id[i]);
                bt[i].setOnClickListener(mHandler);
            }
        }

        Intent intent = getIntent();

        if (intent.getAction() != null)
            if (intent.getAction().equals(Intent.ACTION_GET_CONTENT)) {
                bimg[5].setVisibility(View.GONE);
                mReturnIntent = true;

            } else if (intent.getAction().equals(ACTION_WIDGET)) {
                Log.e("MAIN", "Widget action, string = " + intent.getExtras().getString("location"));
                mHandler.updateDirectory(mFileMag.getNextDir(intent.getExtras().getString("location"), true));

            }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString("location", mFileMag.getCurrentDir());
    }

    /*(non Java-Doc)
     * Returns the file that was selected to the intent that
     * called this activity. usually from the caller is another application.
     */
    private void returnIntentResults(File data) {
        mReturnIntent = false;

        Intent ret = new Intent();
        ret.setData(Uri.fromFile(data));
        setResult(RESULT_OK, ret);

        finish();
    }

    @Override
    public void onListItemClick(ListView parent, View view, int position, long id) {
        final String item = mHandler.getData(position);
        File file = new File(mFileMag.getCurrentDir() + "/" + item);
        String item_ext = null;

        try {
            item_ext = item.substring(item.lastIndexOf("."), item.length());

        } catch (IndexOutOfBoundsException e) {
            item_ext = "";
        }

        {
            if (file.isDirectory()) {
                if (file.canRead()) {
                    mHandler.stopThumbnailThread();
                    mHandler.updateDirectory(mFileMag.getNextDir(item, false));
                    mPathLabel.setText(mFileMag.getCurrentDir());

		    		/*set back button switch to true 
                     * (this will be better implemented later)
		    		 */
                    if (!mUseBackKey)
                        mUseBackKey = true;

                } else {
                    Toast.makeText(this, "Не удается прочитать папку, нету прав",
                            Toast.LENGTH_SHORT).show();
                }
            }

	    	/*music file selected--add more audio formats*/
            else if (item_ext.equalsIgnoreCase(".mp3") ||
                    item_ext.equalsIgnoreCase(".m4a") ||
                    item_ext.equalsIgnoreCase(".mp4")) {

                if (mReturnIntent) {
                    returnIntentResults(file);
                } else {
                    Intent i = new Intent();
                    i.setAction(android.content.Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.fromFile(file), "audio/*");
                    startActivity(i);
                }
            }

	    	/*photo file selected*/
            else if (item_ext.equalsIgnoreCase(".jpeg") ||
                    item_ext.equalsIgnoreCase(".jpg") ||
                    item_ext.equalsIgnoreCase(".png") ||
                    item_ext.equalsIgnoreCase(".gif") ||
                    item_ext.equalsIgnoreCase(".tiff")) {

                if (file.exists()) {
                    if (mReturnIntent) {
                        returnIntentResults(file);

                    } else {
                        Intent picIntent = new Intent();
                        picIntent.setAction(android.content.Intent.ACTION_VIEW);
                        picIntent.setDataAndType(Uri.fromFile(file), "image/*");
                        startActivity(picIntent);
                    }
                }
            }

	    	/*video file selected--add more video formats*/
            else if (item_ext.equalsIgnoreCase(".m4v") ||
                    item_ext.equalsIgnoreCase(".3gp") ||
                    item_ext.equalsIgnoreCase(".wmv") ||
                    item_ext.equalsIgnoreCase(".mp4") ||
                    item_ext.equalsIgnoreCase(".ogg") ||
                    item_ext.equalsIgnoreCase(".wav")) {

                if (file.exists()) {
                    if (mReturnIntent) {
                        returnIntentResults(file);

                    } else {
                        Intent movieIntent = new Intent();
                        movieIntent.setAction(android.content.Intent.ACTION_VIEW);
                        movieIntent.setDataAndType(Uri.fromFile(file), "video/*");
                        startActivity(movieIntent);
                    }
                }
            }
	    	
	    	/*zip file */
            else if (item_ext.equalsIgnoreCase(".zip")) {

                if (mReturnIntent) {
                    returnIntentResults(file);

                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    AlertDialog alert;
                    mZippedTarget = mFileMag.getCurrentDir() + "/" + item;
                    CharSequence[] option = {"Извлечь здесь", "Извлечь в..."};

                    builder.setTitle("Извлечь");
                    builder.setItems(option, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case 0:
                                    String dir = mFileMag.getCurrentDir();
                                    mHandler.unZipFile(item, dir + "/");
                                    break;

                                case 1:
                                    mDetailLabel.setText("Удерживайте " + item +
                                            " для извлечения");
                                    mHoldingZip = true;
                                    break;
                            }
                        }
                    });

                    alert = builder.create();
                    alert.show();
                }
            }
	    	
	    	/* gzip files, this will be implemented later */
            else if (item_ext.equalsIgnoreCase(".gzip") ||
                    item_ext.equalsIgnoreCase(".gz")) {

                if (mReturnIntent) {
                    returnIntentResults(file);

                } else {
                    //TODO:
                }
            }
	    	
	    	/*pdf file selected*/
            else if (item_ext.equalsIgnoreCase(".pdf")) {

                if (file.exists()) {
                    if (mReturnIntent) {
                        returnIntentResults(file);

                    } else {
                        Intent pdfIntent = new Intent();
                        pdfIntent.setAction(android.content.Intent.ACTION_VIEW);
                        pdfIntent.setDataAndType(Uri.fromFile(file),
                                "application/pdf");

                        try {
                            startActivity(pdfIntent);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(this, "Извините, невозможно найти pdf просмотрщик",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
	    	
	    	/*Android application file*/
            else if (item_ext.equalsIgnoreCase(".apk")) {

                if (file.exists()) {
                    if (mReturnIntent) {
                        returnIntentResults(file);

                    } else {
                        Intent apkIntent = new Intent();
                        apkIntent.setAction(android.content.Intent.ACTION_VIEW);
                        apkIntent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
                        startActivity(apkIntent);
                    }
                }
            }
	    	
	    	/* HTML file */
            else if (item_ext.equalsIgnoreCase(".html")) {

                if (file.exists()) {
                    if (mReturnIntent) {
                        returnIntentResults(file);

                    } else {
                        Intent htmlIntent = new Intent();
                        htmlIntent.setAction(android.content.Intent.ACTION_VIEW);
                        htmlIntent.setDataAndType(Uri.fromFile(file), "text/html");

                        try {
                            startActivity(htmlIntent);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(this, "Извините, невозможно найти HTML браузер",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
	    	
	    	/* text file*/
            else if (item_ext.equalsIgnoreCase(".txt")) {

                if (file.exists()) {
                    if (mReturnIntent) {
                        returnIntentResults(file);

                    } else {
                        Intent txtIntent = new Intent();
                        txtIntent.setAction(android.content.Intent.ACTION_VIEW);
                        txtIntent.setDataAndType(Uri.fromFile(file), "text/plain");

                        try {
                            startActivity(txtIntent);
                        } catch (ActivityNotFoundException e) {
                            txtIntent.setType("text/*");
                            startActivity(txtIntent);
                        }
                    }
                }
            }
	    	
	    	/* generic intent */
            else {
                if (file.exists()) {
                    if (mReturnIntent) {
                        returnIntentResults(file);

                    } else {
                        Intent generic = new Intent();
                        generic.setAction(android.content.Intent.ACTION_VIEW);
                        generic.setDataAndType(Uri.fromFile(file), "text/plain");

                        try {
                            startActivity(generic);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(this, "Извините, ничего не найдено " +
                                    "для открытия " + file.getName(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        }
    }

    /* ================Menus, options menu and context menu start here=================*/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_MKDIR, 0, "Создать").setIcon(R.drawable.newfolder);
        menu.add(0, MENU_SEARCH, 0, "Поиск").setIcon(R.drawable.search);
        menu.add(0, MENU_QUIT, 0, "Выход").setIcon(R.drawable.logout);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_MKDIR:
                showDialog(MENU_MKDIR);
                return true;

            case MENU_SEARCH:
                showDialog(MENU_SEARCH);
                return true;

            case MENU_QUIT:
                finish();
                return true;
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo info) {
        super.onCreateContextMenu(menu, v, info);

        AdapterContextMenuInfo _info = (AdapterContextMenuInfo) info;
        mSelectedListItem = mHandler.getData(_info.position);

        if (mFileMag.isDirectory(mSelectedListItem)) {
            menu.setHeaderTitle("Операции с папкой");
            menu.add(0, D_MENU_DELETE, 0, "Удалить");
            menu.add(0, D_MENU_RENAME, 0, "Переименовать");
            menu.add(0, D_MENU_COPY, 0, "Копировать");
            menu.add(0, D_MENU_MOVE, 0, "Переместить");
            menu.add(0, D_MENU_ZIP, 0, "Заархивировать");
            menu.add(0, D_MENU_PASTE, 0, "Вставить").setEnabled(mHoldingFile);
            menu.add(0, D_MENU_UNZIP, 0, "Извлечь здесь").setEnabled(mHoldingZip);

        } else if (!mFileMag.isDirectory(mSelectedListItem)) {
            menu.setHeaderTitle("Операции с файлом");
            menu.add(0, F_MENU_DELETE, 0, "Удалить");
            menu.add(0, F_MENU_RENAME, 0, "Переименовать");
            menu.add(0, F_MENU_COPY, 0, "Копировать");
            menu.add(0, F_MENU_MOVE, 0, "Переместить");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case D_MENU_DELETE:
            case F_MENU_DELETE:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Внимание ");
                builder.setIcon(R.drawable.warning);
                builder.setMessage("Удаление " + mSelectedListItem +
                        " не может быть отменено. Вы уверены, что хотите удалить?");
                builder.setCancelable(false);

                builder.setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.setPositiveButton("Удалить", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mHandler.deleteFile(mFileMag.getCurrentDir() + "/" + mSelectedListItem);
                    }
                });
                AlertDialog alert_d = builder.create();
                alert_d.show();
                return true;

            case D_MENU_RENAME:
                showDialog(D_MENU_RENAME);
                return true;

            case F_MENU_RENAME:
                showDialog(F_MENU_RENAME);
                return true;

            case F_MENU_MOVE:
            case D_MENU_MOVE:
            case F_MENU_COPY:
            case D_MENU_COPY:
                if (item.getItemId() == F_MENU_MOVE || item.getItemId() == D_MENU_MOVE)
                    mHandler.setDeleteAfterCopy(true);

                mHoldingFile = true;

                mCopiedTarget = mFileMag.getCurrentDir() + "/" + mSelectedListItem;
                mDetailLabel.setText("Ожидание " + mSelectedListItem);
                return true;


            case D_MENU_PASTE:

                if (mHoldingFile && mCopiedTarget.length() > 1) {

                    mHandler.copyFile(mCopiedTarget, mFileMag.getCurrentDir() + "/" + mSelectedListItem);
                    mDetailLabel.setText("");
                }

                mHoldingFile = false;
                return true;

            case D_MENU_ZIP:
                String dir = mFileMag.getCurrentDir();

                mHandler.zipFile(dir + "/" + mSelectedListItem);
                return true;

            case D_MENU_UNZIP:
                if (mHoldingZip && mZippedTarget.length() > 1) {
                    String current_dir = mFileMag.getCurrentDir() + "/" + mSelectedListItem + "/";
                    String old_dir = mZippedTarget.substring(0, mZippedTarget.lastIndexOf("/"));
                    String name = mZippedTarget.substring(mZippedTarget.lastIndexOf("/") + 1, mZippedTarget.length());

                    if (new File(mZippedTarget).canRead() && new File(current_dir).canWrite()) {
                        mHandler.unZipFileToDir(name, current_dir, old_dir);
                        mPathLabel.setText(current_dir);

                    } else {
                        Toast.makeText(this, "У вас нет разрешения для распаковки " + name,
                                Toast.LENGTH_SHORT).show();
                    }
                }

                mHoldingZip = false;
                mDetailLabel.setText("");
                mZippedTarget = "";
                return true;
        }
        return false;
    }
    
    /* ================Menus, options menu and context menu end here=================*/

    @Override
    protected Dialog onCreateDialog(int id) {
        final Dialog dialog = new Dialog(Main.this);

        switch (id) {
            case MENU_MKDIR:
                dialog.setContentView(R.layout.input_layout);
                dialog.setTitle("Создание новой папки");
                dialog.setCancelable(false);

                ImageView icon = (ImageView) dialog.findViewById(R.id.input_icon);
                icon.setImageResource(R.drawable.newfolder);

                TextView label = (TextView) dialog.findViewById(R.id.input_label);
                label.setText(mFileMag.getCurrentDir());
                final EditText input = (EditText) dialog.findViewById(R.id.input_inputText);

                Button cancel = (Button) dialog.findViewById(R.id.input_cancel_b);
                Button create = (Button) dialog.findViewById(R.id.input_create_b);

                create.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        if (input.getText().length() > 1) {
                            if (mFileMag.createDir(mFileMag.getCurrentDir() + "/", input.getText().toString()) == 0)
                                Toast.makeText(Main.this,
                                        "Папка " + input.getText().toString() + " создана",
                                        Toast.LENGTH_LONG).show();
                            else
                                Toast.makeText(Main.this, "Новая папка не была создана", Toast.LENGTH_SHORT).show();
                        }

                        dialog.dismiss();
                        String temp = mFileMag.getCurrentDir();
                        mHandler.updateDirectory(mFileMag.getNextDir(temp, true));
                    }
                });
                cancel.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                break;
            case D_MENU_RENAME:
            case F_MENU_RENAME:
                dialog.setContentView(R.layout.input_layout);
                dialog.setTitle("Переименовать " + mSelectedListItem);
                dialog.setCancelable(false);

                ImageView rename_icon = (ImageView) dialog.findViewById(R.id.input_icon);
                rename_icon.setImageResource(R.drawable.rename);

                TextView rename_label = (TextView) dialog.findViewById(R.id.input_label);
                rename_label.setText(mFileMag.getCurrentDir());
                final EditText rename_input = (EditText) dialog.findViewById(R.id.input_inputText);

                Button rename_cancel = (Button) dialog.findViewById(R.id.input_cancel_b);
                Button rename_create = (Button) dialog.findViewById(R.id.input_create_b);
                rename_create.setText("Переименовать");

                rename_create.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        if (rename_input.getText().length() < 1)
                            dialog.dismiss();

                        if (mFileMag.renameTarget(mFileMag.getCurrentDir() + "/" + mSelectedListItem, rename_input.getText().toString()) == 0) {
                            Toast.makeText(Main.this, mSelectedListItem + " был переименован в " + rename_input.getText().toString(),
                                    Toast.LENGTH_LONG).show();
                        } else
                            Toast.makeText(Main.this, mSelectedListItem + " не был переименован", Toast.LENGTH_LONG).show();

                        dialog.dismiss();
                        String temp = mFileMag.getCurrentDir();
                        mHandler.updateDirectory(mFileMag.getNextDir(temp, true));
                    }
                });
                rename_cancel.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                break;

            case MENU_SEARCH:
                dialog.setContentView(R.layout.input_layout);
                dialog.setTitle("Поиск");
                dialog.setCancelable(false);

                ImageView searchIcon = (ImageView) dialog.findViewById(R.id.input_icon);
                searchIcon.setImageResource(R.drawable.search);

                TextView search_label = (TextView) dialog.findViewById(R.id.input_label);
                search_label.setText("Поиск файла");
                final EditText search_input = (EditText) dialog.findViewById(R.id.input_inputText);

                Button search_button = (Button) dialog.findViewById(R.id.input_create_b);
                Button cancel_button = (Button) dialog.findViewById(R.id.input_cancel_b);
                search_button.setText("Поиск");

                search_button.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        String temp = search_input.getText().toString();

                        if (temp.length() > 0)
                            mHandler.searchForFile(temp);
                        dialog.dismiss();
                    }
                });

                cancel_button.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });

                break;
        }
        return dialog;
    }
}
