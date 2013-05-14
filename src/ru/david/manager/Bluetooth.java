package ru.david.manager;

import java.util.ArrayList;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class Bluetooth extends Activity {

	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

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
	private TableRow mConversationArrayAdapter;
	// Буфер строки для исходящих сообщений
	private StringBuffer mOutStringBuffer;
	// Локальный Bluetooth адаптер
	private BluetoothAdapter mBluetoothAdapter = null;
	// Объект для сервиса чата
	private BluetoothService mChatService = null;

	private FileManager mFileManager;
	private String mCurrentPath = "/sdcard";

	private boolean connected = false;

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
		mConversationView = (ListView) findViewById(R.id.in);
		mConversationArrayAdapter = new TableRow();
		mConversationView.setAdapter(mConversationArrayAdapter);
		mConversationView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				String item = (String) mConversationArray
						.get(position);
				if (item.lastIndexOf(".") > -1)
					return;
				mCurrentPath = "/" + item;
				mChatService.writeCommand(BluetoothService.FS_DIR,
						item.getBytes());
			}
		});

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
					mConversationArray.clear();

					mChatService.writeCommand(BluetoothService.FS_DIR,
							"sdcard".getBytes());
					break;
				case BluetoothService.STATE_CONNECTING:
					mTitle.setText(R.string.title_connecting);
					break;
				case BluetoothService.STATE_LISTEN:
				case BluetoothService.STATE_NONE:
					mTitle.setText(R.string.title_not_connected);
					break;
				}
				break;
			case MESSAGE_WRITE:
				byte[] writeBuf = (byte[]) msg.obj;
				// construct a string from the buffer
				String writeMessage = new String(writeBuf);
				mConversationArray.add("Я:  " + writeMessage);
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				String readMessage = new String(readBuf, 0, msg.arg1);
				// mConversationArrayAdapter.add(mConnectedDeviceName+":  " +
				// readMessage);
				break;
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
			case BluetoothService.FS_COMMAND:
				switch (msg.arg1) {
				case BluetoothService.FS_DIR:
					ArrayList<String> dir = mFileManager.getNextDir(new String(
							(byte[]) msg.obj), true);
					String answer = "";
					for (String string : dir) {
						answer += string + "==";
					}
					answer = answer.substring(0, answer.length() - 2);
					mChatService.writeCommand(
							BluetoothService.FS_COMMAND_ANSWER,
							answer.getBytes());
					break;
				case BluetoothService.FS_COMMAND_ANSWER:
					byte[] readBuf1 = (byte[]) msg.obj;
					String readMessage1 = new String(readBuf1, 0, msg.arg1);
					// mConversationArrayAdapter.clear();
					do {
						int index = readMessage1.indexOf("==");
						if (index == -1) {
							mConversationArray.add(readMessage1);
							break;
						} else {
							mConversationArray.add(readMessage1
									.substring(0, index));
							readMessage1 = readMessage1.substring(index);
						}
					} while (true);
					break;
				}
				break;
			}

		}
	};

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
			// Launch the DeviceListActivity to see devices and do scan
			Intent serverIntent = new Intent(this, BluetoothActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			return true;
		case R.id.discoverable:
			// Ensure this device is discoverable by others
			ensureDiscoverable();
			mFileManager = new FileManager();
			return true;
		}
		return false;
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
		private final int mColor = Color.WHITE;

		public TableRow() {
			super(getApplicationContext(), R.layout.tablerow, mConversationArray);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final ViewHolder mViewHolder;
			int num_items = 0;
			String file = mConversationArray.get(position);

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
				if (file.lastIndexOf(".") > -1) {
					String sub_ext = file.substring(file.lastIndexOf(".") + 1);

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

				} else {
					mViewHolder.icon.setImageResource(R.drawable.folder);
				}
				mViewHolder.topView.setText(file);
			}
			return convertView;
		}

	}
}