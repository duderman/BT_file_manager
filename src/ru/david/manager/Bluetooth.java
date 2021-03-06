package ru.david.manager;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import java.io.*;
import java.util.ArrayList;

public class Bluetooth extends ListActivity {

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    // Constants with fs comands
    public static final int FS_COMMAND = 6;
    public static final int FS_COMMAND_ANSWER = 7;
    public static final int FS_DIR = 8;
    public static final int FS_BACK = 9;
    public static final int FS_HOME = 10;
    public static final int FS_FILE = 11;
    public static final int FS_FILE_ANSWER = 12;
    public static final int FS_ERROR = 13;

    public static final int MAX_FILE_SIZE = 161280;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Намерение запросить подключение
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Layout Views
    private TextView mTitle;
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    // Название подключенного устройства
    private String mConnectedDeviceName = null;
    // Массив адаптера для разговорного потока
    private ArrayList<String> mConversationArray;
    private ArrayList<String> mFileTypesArray;
    private TableRow mConversationArrayAdapter;
    // Буфер строки для исходящих сообщений
    private StringBuffer mOutStringBuffer;
    // Локальный Bluetooth адаптер
    private BluetoothAdapter mBluetoothAdapter = null;
    // Объект для сервиса чата
    private BluetoothService mChatService = null;

    private FileManager mFileManager;
    private String mCurrentPath = "/sdcard";
    private String mCurrentItem = "";

    private boolean connected = false;

    private boolean isServer = false;

    //controls
    private ImageButton backButton;
    private ImageButton homeButton;
    private TextView pathTextView;
    private HorizontalScrollView buttonsHolder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Настройка расположения слоя
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.bluetooth_layout);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
                R.layout.custom_title);

        // Расположение титров для custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);

        // Получение локального Bluetooth адаптера
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Проверка на доступность блютуза
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        backButton = (ImageButton) findViewById(R.id.blt_back_button);
        homeButton = (ImageButton) findViewById(R.id.blt_home_button);
        pathTextView = (TextView) findViewById(R.id.blt_path_label);
        buttonsHolder = (HorizontalScrollView) findViewById(R.id.blt_buttons_view);
        backButton.setOnClickListener(myOnClickListener);
        homeButton.setOnClickListener(myOnClickListener);
        switchControlVisibility(View.GONE);
    }

    private void switchControlVisibility(int visibility) {
        if (visibility != View.GONE && visibility != View.VISIBLE)
            return;
        else {
            pathTextView.setVisibility(visibility);
            buttonsHolder.setVisibility(visibility);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupUserInterface() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the Bluetooth session
        } else {
            if (mChatService == null)
                setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity
        // returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't
            // started already
            if (mChatService.getState() == BluetoothService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    private void setupChat() {
        // Initialize the array adapter for the conversation thread
        mConversationArray = new ArrayList<String>();
        mFileTypesArray = new ArrayList<String>();
        mConversationView = getListView();
        mConversationArrayAdapter = new TableRow();
        mConversationView.setAdapter(mConversationArrayAdapter);
        setListAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String item = (String) mConversationArray
                .get(position);
        String type = mFileTypesArray.get(position);
        if (type.equals("F_")) {
            mConversationView.setEnabled(false);
            mChatService.write(("FS_FILE:" + item).getBytes());
            mCurrentItem = item;
        } else if (type.equals("D_")) {
            setCurrentPath(mCurrentPath + "/" + item);
            mChatService.write(("FS_DIR:" + item).getBytes());
        }
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null)
            mChatService.stop();
    }

    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(
                    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId,
                                      KeyEvent event) {
            // If the action is a key-up event on the return key, send the
            // message
            if (actionId == EditorInfo.IME_NULL
                    && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };


    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            mTitle.setText("Подключено к ");
                            mTitle.append(mConnectedDeviceName);
                            clearConversation(true);
                            if (!isServer) {
                                mChatService.write("FS_DIR:sdcard".getBytes());
                                setCurrentPath("/sdcard");
                                switchControlVisibility(View.VISIBLE);
                            }
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            mTitle.setText(R.string.title_connecting);
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            mTitle.setText(R.string.title_not_connected);
                            isServer = false;
                            setCurrentPath("/");
                            clearConversation(true);
                            switchControlVisibility(View.GONE);
                            break;
                    }
                    break;
//                case MESSAGE_WRITE:
//                    byte[] writeBuf = (byte[]) msg.obj;
//                    // construct a string from the buffer
//                    String writeMessage = new String(writeBuf);
//                    mConversationArray.add("Я:  " + writeMessage);
//                    break;
//                case MESSAGE_READ:
//                    byte[] readBuf = (byte[]) msg.obj;
//                    String readMessage = new String(readBuf, 0, msg.arg1);
//                    // mConversationArrayAdapter.add(mConnectedDeviceName+":  " +
//                    // readMessage);
//                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(),
                            "Подключено к " + mConnectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(),
                            msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
                            .show();
                    break;
                case FS_COMMAND:
                    switch (msg.arg1) {
                        case FS_DIR:
                        case FS_BACK:
                        case FS_HOME:
                            if (isServer) {
                                addChatRecord("Получено сообщение " + msg.arg1 + " от " + mConnectedDeviceName);
                                ArrayList<String> dir = new ArrayList<String>();
                                if (msg.arg1 == FS_DIR)
                                    dir = mFileManager.getNextDir(new String((byte[]) msg.obj, 0, msg.arg2), false, true);
                                else if (msg.arg1 == FS_BACK)
                                    dir = mFileManager.getPreviousDir(true);
                                else
                                    dir = mFileManager.setHomeDir("/sdcard", true);
                                String answer = "";
                                for (String string : dir) {
                                    answer += string + "==";
                                }
                                answer = answer.substring(0, answer.length() - 2);
                                answer = "FS_COMMAND_ANSWER:" + answer;
                                mChatService.write(answer.getBytes());
                            }
                            break;
                        case FS_FILE:
                            if (isServer) {
                                addChatRecord("Получено сообщение " + msg.arg1 + " от " + mConnectedDeviceName);
                                String fileName = mFileManager.getCurrentDir() + "/" + new String((byte[]) msg.obj, 0, msg.arg2);
                                File file = new File(fileName);
                                if ("FS_COMMAND_ANSWER".getBytes().length + file.length() < MAX_FILE_SIZE) {
                                    try {
                                        FileInputStream fis = new FileInputStream(file);
                                        byte[] fileByBytes = new byte[fis.available()];
                                        fis.read(fileByBytes, 0, fis.available());
                                        fis.close();
                                        mChatService.write(concatArrays("FS_FILE_ANSWER:".getBytes(), fileByBytes));
                                    } catch (FileNotFoundException e) {
                                        addChatRecord("Файл " + fileName + " не найден");
                                        mChatService.write("FS_ERROR:Файл не найден".getBytes());
                                        Log.e("FS_ERROR", "File " + fileName + " not found");
                                    } catch (IOException e) {
                                        addChatRecord("Ошибка при кодировании файла");
                                        mChatService.write("FS_ERROR:Непредвиденная ошибка".getBytes());
                                        Log.e("FS_ERROR", "Error when packing bytes of file " + fileName);
                                    }
                                } else {
                                    addChatRecord("Файл слишком большой");
                                    mChatService.write("FS_ERROR:Файл слишком большой".getBytes());
                                    Log.e("FS_ERROR", "File too large too send over Bluetooth " + fileName);
                                }

                            }
                            break;
                        case FS_FILE_ANSWER:
                            File file = new File(getExternalFilesDir(null), mCurrentItem);
                            try {
                                FileOutputStream fos = new FileOutputStream(file);
                                fos.write((byte[]) msg.obj);
                                fos.close();
                                Intent intent = new Intent();
                                intent.setAction(android.content.Intent.ACTION_VIEW);
                                intent.setDataAndType(Uri.fromFile(file), "*/*");
                                startActivity(intent);
                            } catch (IOException e) {
                                Toast.makeText(getApplicationContext(), "Не удалось получить файл", Toast.LENGTH_LONG).show();
                                Log.e("FS_ERROR", "Error while saving temp file");
                            } finally {
                                mConversationView.setEnabled(true);
                            }
                            break;
                        case FS_COMMAND_ANSWER:
                            byte[] readBuf1 = (byte[]) msg.obj;
                            String readMessage1 = new String(readBuf1, 0, msg.arg2);
                            clearConversation(false);
                            do {
                                mFileTypesArray.add(readMessage1.substring(0, 2));
                                readMessage1 = readMessage1.substring(2);
                                int index = readMessage1.indexOf("==");
                                if (index == -1) {
                                    mConversationArray.add(readMessage1);
                                    break;
                                } else {
                                    mConversationArray.add(readMessage1
                                            .substring(0, index));
                                    readMessage1 = readMessage1.substring(index + 2);
                                }
                            } while (true);
                            mConversationArrayAdapter.notifyDataSetChanged();
                            break;
                        case FS_ERROR:
                            String error = new String((byte[]) msg.obj, 0, msg.arg2);
                            Toast.makeText(getApplicationContext(), "Ошибка: " + error, Toast.LENGTH_LONG).show();
                            Log.e("FS_ERROR", error);
                            break;
                    }
                    break;
            }

        }
    };

    private void addChatRecord(String msg, String type) {
        if (msg != null && type != null) {
            mConversationArray.add(msg);
            mFileTypesArray.add(type);
            mConversationArrayAdapter.notifyDataSetChanged();
        }
    }

    private void addChatRecord(String msg) {
        addChatRecord(msg, "N_");
    }

    private byte[] concatArrays(byte[]... arrays) {
        // Determine the length of the result array
        int totalLength = 0;
        for (int i = 0; i < arrays.length; i++) {
            totalLength += arrays[i].length;
        }

        // create the result array
        byte[] result = new byte[totalLength];

        // copy the source arrays into the result array
        int currentIndex = 0;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, currentIndex, arrays[i].length);
            currentIndex += arrays[i].length;
        }

        return result;
    }

    private void clearConversation(boolean withNotify) {
        mConversationArray.clear();
        mFileTypesArray.clear();
        if (withNotify)
            mConversationArrayAdapter.notifyDataSetChanged();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras().getString(
                            BluetoothActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter
                            .getRemoteDevice(address);
                    // Attempt to connect to the device
                    mChatService.connect(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occured
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, R.id.scan, 0, "Клиент").setIcon(R.drawable.search);
        menu.add(0, R.id.discoverable, 0, "Сервер").setIcon(
                R.drawable.newfolder);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan:
                isServer = false;
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, BluetoothActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                return true;
            case R.id.discoverable:
                // Ensure this device is discoverable by others
                this.isServer = true;
                mFileManager = new FileManager();
                ensureDiscoverable();
                return true;
        }
        return false;
    }

    private static class ViewHolder {
        TextView topView;
        TextView bottomView;
        ImageView icon;
    }

    private class TableRow extends ArrayAdapter<String> {
        private final int KB = 1024;
        private final int MG = KB * KB;
        private final int GB = MG * KB;
        private final int mColor = Color.WHITE;

        public TableRow() {
            super(getApplicationContext(), R.layout.tablerow, mConversationArray);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder mViewHolder;
            int num_items = 0;
            String file = mConversationArray.get(position);
            String type = mFileTypesArray.get(position);

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getApplicationContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater
                        .inflate(R.layout.tablerow, parent, false);

                mViewHolder = new ViewHolder();
                mViewHolder.topView = (TextView) convertView
                        .findViewById(R.id.top_view);
                mViewHolder.bottomView = (TextView) convertView
                        .findViewById(R.id.bottom_view);
                mViewHolder.icon = (ImageView) convertView
                        .findViewById(R.id.row_image);

                convertView.setTag(mViewHolder);

            } else {
                mViewHolder = (ViewHolder) convertView.getTag();
            }

            mViewHolder.topView.setTextColor(mColor);
            mViewHolder.bottomView.setTextColor(mColor);

            if (file != null) {
                if (type.equals("F_")) {
                    String sub_ext = "";
                    if (file.lastIndexOf(".") + 1 < file.length())
                        sub_ext = file.substring(file.lastIndexOf(".") + 1);

					/*
                     * This series of else if statements will determine which
					 * icon is displayed
					 */

                    if (sub_ext.equalsIgnoreCase("pdf")) {
                        mViewHolder.icon.setImageResource(R.drawable.pdf);

                    } else if (sub_ext.equalsIgnoreCase("mp3")
                            || sub_ext.equalsIgnoreCase("wma")
                            || sub_ext.equalsIgnoreCase("m4a")
                            || sub_ext.equalsIgnoreCase("m4p")) {

                        mViewHolder.icon.setImageResource(R.drawable.music);

                    } else if (sub_ext.equalsIgnoreCase("png")
                            || sub_ext.equalsIgnoreCase("jpg")
                            || sub_ext.equalsIgnoreCase("jpeg")
                            || sub_ext.equalsIgnoreCase("gif")
                            || sub_ext.equalsIgnoreCase("tiff")) {
                        mViewHolder.icon.setImageResource(R.drawable.image);
                    } else if (sub_ext.equalsIgnoreCase("zip")
                            || sub_ext.equalsIgnoreCase("gzip")
                            || sub_ext.equalsIgnoreCase("gz")) {

                        mViewHolder.icon.setImageResource(R.drawable.zip);

                    } else if (sub_ext.equalsIgnoreCase("m4v")
                            || sub_ext.equalsIgnoreCase("wmv")
                            || sub_ext.equalsIgnoreCase("3gp")
                            || sub_ext.equalsIgnoreCase("mp4")) {

                        mViewHolder.icon.setImageResource(R.drawable.movies);

                    } else if (sub_ext.equalsIgnoreCase("doc")
                            || sub_ext.equalsIgnoreCase("docx")) {

                        mViewHolder.icon.setImageResource(R.drawable.word);

                    } else if (sub_ext.equalsIgnoreCase("xls")
                            || sub_ext.equalsIgnoreCase("xlsx")) {

                        mViewHolder.icon.setImageResource(R.drawable.excel);

                    } else if (sub_ext.equalsIgnoreCase("ppt")
                            || sub_ext.equalsIgnoreCase("pptx")) {

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

                } else if (type.equals("D_")) {
                    mViewHolder.icon.setImageResource(R.drawable.folder);
                }
                mViewHolder.topView.setText(file);
            }
            return convertView;
        }

    }

    OnClickListener myOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {

            switch (v.getId()) {

                case R.id.blt_back_button:
                    if (mCurrentPath != "/") {
                        setCurrentPath(mCurrentPath.substring(0, mCurrentPath.lastIndexOf("/")));
                        mChatService.write(("FS_BACK:").getBytes());
                    }
                    break;

                case R.id.blt_home_button:
                    setCurrentPath("/sdcard");
                    mChatService.write(("FS_HOME:").getBytes());
                    break;
            }
        }
    };

    private void setCurrentPath(String path) {
        if (path == "" || path.indexOf('/') == -1)
            return;
        mCurrentPath = path;
        pathTextView.setText(path);
    }
}