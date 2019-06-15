package com.example.mailbuddy;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    public static final String SHARED_PREFS = "MailbuddySharedPrefs";

    //Arduino requests
    final String REQUEST_CHECK_MAIL = "1";
    final String REQUEST_TOGGLE_HALL_READ = "2";
    final String REQUEST_RESET = "3";
    final String REQUEST_TOGGLE_LED = "4";
    final String REQUEST_START_MONITORING = "5";

    //GUI components
    private TextView sensorReading, getSensorReading;
    private TextView mailBuddyConnected, getMailBuddyConnected;
    private TextView generalOutput;
    private Button check;
    private Button showPairedDevices;
    private Button toggleReadingSensor;
    private Button mailboxEmptied;
    private Button discoverNewDevices;

    //Bluetooth stuff

    private BluetoothAdapter BTAdapter;

    private Set<BluetoothDevice> pairedDevices;
    private ArrayAdapter<String> BTArrayAdapter;
    private ArrayList listFoundDevices = new ArrayList<>();

    private RecyclerView devicesRecyclerView;
    private RecyclerView.Adapter recyclerViewAdapter;
    private RecyclerView.LayoutManager recyclerViewLayoutManager;


    //Other components
    private Handler messageHandler;
    private ConnectedThread connectedThread;
    private BluetoothSocket BTSocket = null;
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    //#defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1;
    private final static int MESSAGE_READ = 2;
    private final static int CONNECTING_STATUS = 3;

    private SharedPreferences mPreferences;
    private SharedPreferences.Editor mEditor;

    String loadedMacAddress;
    String loadedName;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mEditor = mPreferences.edit();

        loadedMacAddress = mPreferences.getString(getString(R.string.shared_pref_mailbuddy_mac), "");
        loadedName = mPreferences.getString(getString(R.string.shared_pref_mailbuddy_name), "");


        mailBuddyConnected = (TextView) findViewById(R.id.mailBuddyConnected);
        getMailBuddyConnected = (TextView) findViewById(R.id.mailBuddyConnectedPlaceholder);
        sensorReading = (TextView) findViewById(R.id.sensorReading);
        getSensorReading = (TextView) findViewById(R.id.sensorReadingPlaceholder);

        generalOutput = (TextView) findViewById(R.id.generalOutput);

        //Set up buttons
        check = (Button) findViewById(R.id.check);
        showPairedDevices = (Button) findViewById(R.id.showPairedDevices);
        toggleReadingSensor = (Button) findViewById(R.id.toggleReadingSensor);
        mailboxEmptied = (Button) findViewById(R.id.mailboxEmptied);

        BTArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        BTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        devicesRecyclerView = (RecyclerView) findViewById(R.id.devicesList);
        devicesRecyclerView.setHasFixedSize(true);
        recyclerViewLayoutManager = new LinearLayoutManager(this);
        devicesRecyclerView.setLayoutManager(recyclerViewLayoutManager);

        recyclerViewAdapter = new MyAdapter(listFoundDevices, this);
        devicesRecyclerView.setAdapter(recyclerViewAdapter);

        devicesRecyclerView.addOnItemTouchListener(new RecyclerViewTouchListener(getApplicationContext(), devicesRecyclerView, new RecyclerViewClickListener() {
            @Override
            public void onClick(View view, int position) {
                Toast.makeText(getApplicationContext(), position + " is clicked!", Toast.LENGTH_SHORT).show();

                if (!BTAdapter.isEnabled()) {
                    Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                    return;
                }

                Log.d("Try to connect", "RecycleView item clicked: " + position);

                // Get the device MAC address, which is the last 17 chars in the View
                final String address = ((MyAdapter) devicesRecyclerView.getAdapter()).getDevice(position).getDesc();
                final String name = ((MyAdapter) devicesRecyclerView.getAdapter()).getDevice(position).getHeader();

                generalOutput.setText("Trying to connect to " + name + "...");

                // Spawn a new thread to avoid blocking the GUI one
                new Thread() {
                    public void run() {
                        boolean fail = false;

                        BluetoothDevice device = BTAdapter.getRemoteDevice(address);

                        try {
                            BTSocket = createBluetoothSocket(device);
                        } catch (IOException e) {
                            fail = true;
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                        // Establish the Bluetooth socket connection.
                        try {
                            BTSocket.connect();
                        } catch (IOException e) {
                            try {
                                fail = true;
                                BTSocket.close();
                                messageHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                        .sendToTarget();
                            } catch (IOException e2) {
                                //insert code to deal with this
                                Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                        if (fail == false) {
                            mEditor.putString(getString(R.string.shared_pref_mailbuddy_mac), address);
                            mEditor.putString(getString(R.string.shared_pref_mailbuddy_name), name);
                            mEditor.commit();
                            Log.d("Mailbuddy device saved", address);
                            connectedThread = new ConnectedThread(BTSocket);
                            connectedThread.start();
                            messageHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                    .sendToTarget();
                        }
                    }
                }.start();
            }

            @Override
            public void onLongClick(View view, int position) {
                Toast.makeText(getApplicationContext(), position + " is long pressed!", Toast.LENGTH_SHORT).show();
            }
        }));

        if (BTArrayAdapter == null) {
            // Device does not support Bluetooth
            generalOutput.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(), "Bluetooth device not found!", Toast.LENGTH_SHORT).show();
        } else {

            check.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendRequest(REQUEST_CHECK_MAIL);
                }
            });

            showPairedDevices.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //discover(v);
                    listPairedDevices(v);
                }
            });

            toggleReadingSensor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendRequest(REQUEST_TOGGLE_HALL_READ);
                }
            });

            mailboxEmptied.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendRequest(REQUEST_RESET);
                }
            });


            if (foundSavedMailbuddy()) {
                // Spawn a new thread to avoid blocking the GUI one
                new Thread() {
                    public void run() {
                        boolean fail = false;

                        BluetoothDevice device = BTAdapter.getRemoteDevice(loadedMacAddress);

                        try {
                            BTSocket = createBluetoothSocket(device);
                        } catch (IOException e) {
                            fail = true;
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                        // Establish the Bluetooth socket connection.
                        try {
                            BTSocket.connect();
                        } catch (IOException e) {
                            try {
                                fail = true;
                                BTSocket.close();
                                messageHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                        .sendToTarget();
                            } catch (IOException e2) {
                                //insert code to deal with this
                                Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                        if (fail == false) {
                            connectedThread = new ConnectedThread(BTSocket);
                            connectedThread.start();
                            messageHandler.obtainMessage(CONNECTING_STATUS, 1, -1, loadedName)
                                    .sendToTarget();
                        }
                    }
                }.start();
            }
        }

        //Checking bluetooth messages
        messageHandler = new Handler() {
            final String INFO_MAIL_STATUS = "mail_status";
            final String INFO_HALL_SENSOR_READING = "hall_reading";
            final String INFO_GENERAL_OUTPUT = "general_output";
            final String INFO_CONNECTION_STATUS = "connection_status";
            String messageCategory;
            String messageContent;
            int messageDividerIndex;

            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_READ) {
                    String readMessage = (String) msg.obj;
                    if (readMessage != null) {
                        readMessage = readMessage.trim();
                        messageDividerIndex = readMessage.indexOf(":");
                        if (messageDividerIndex != -1) {
                            messageCategory = readMessage.substring(0, messageDividerIndex);
                            messageContent = readMessage.substring(messageDividerIndex + 1, readMessage.length());

                            switch (messageCategory) {
                                case INFO_GENERAL_OUTPUT:
                                    generalOutput.setText(messageContent);
                                    break;
                                case INFO_MAIL_STATUS:
                                    if (messageContent.equals("1")) {
                                        generalOutput.setTextColor(Color.GREEN);
                                        generalOutput.setText(R.string.new_mail);
                                    } else {
                                        generalOutput.setText(R.string.no_new_mail);
                                        generalOutput.setTextColor(Color.RED);
                                    }
                                    break;
                                case INFO_HALL_SENSOR_READING:
                                    getSensorReading.setText(messageContent);
                                    break;
                                default:
                                    generalOutput.setText(messageContent);
                            }
                        }
//                        generalOutput.setText("Category: " + messageCategory + " - Content: " + messageContent);
                        Log.d("BT MESSAGE", "Category: " + messageCategory + " - Content:" + messageContent + "ENDING");
                    }
                }
                if (msg.what == CONNECTING_STATUS) {
                    if (msg.arg1 == 1) {
                        getMailBuddyConnected.setText("Yes");
                        if (!foundSavedMailbuddy()) {
                            generalOutput.setText("Connected to " + (String) (msg.obj));
                        }
                        if(foundSavedMailbuddy()){
                            sendRequest(REQUEST_CHECK_MAIL);
                        }
                    } else {
                        generalOutput.setText("Connection failed");
                    }
                }
            }
        };
    }

    @Override
    protected void onResume(){
        super.onResume();
        sendRequest(REQUEST_CHECK_MAIL);
    }

    boolean foundSavedMailbuddy() {
        return (!loadedMacAddress.isEmpty() && !loadedName.isEmpty()) ? true : false;
    }

    private void discover(View view) {
        // Check if the device is already discovering
        if (BTAdapter.isDiscovering()) {
            BTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(), "Discovery stopped", Toast.LENGTH_SHORT).show();
        } else {
            if (BTAdapter.isEnabled()) {
                listFoundDevices.clear();
                recyclerViewAdapter.notifyDataSetChanged();
                BTArrayAdapter.clear(); // clear items
                BTAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();
                registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            } else {
                Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void listPairedDevices(View view) {
        pairedDevices = BTAdapter.getBondedDevices();
        if (!listFoundDevices.isEmpty()) {
            listFoundDevices.clear();
            recyclerViewAdapter.notifyDataSetChanged();
        }
        if (BTAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : pairedDevices) {
                listFoundDevices.add(new ListItem(device.getName(), device.getAddress()));
                recyclerViewAdapter.notifyDataSetChanged();
            }
            Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }

    private void bluetoothOn(View view) {
        if (!BTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            getMailBuddyConnected.setText("Bluetooth enabled");
            Toast.makeText(getApplicationContext(), "Bluetooth turned on", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "Bluetooth is already on", Toast.LENGTH_SHORT).show();
        }
    }

    private void bluetoothOff(View view) {
        BTAdapter.disable(); // turn off
        getMailBuddyConnected.setText("Bluetooth disabled");
        Toast.makeText(getApplicationContext(), "Bluetooth turned Off", Toast.LENGTH_SHORT).show();
    }

    private void sendRequest(String request) {
        if (connectedThread != null) {
            connectedThread.write(request);
            switch (request) {
                case REQUEST_CHECK_MAIL:
                    Toast.makeText(getApplicationContext(), "Checking for new mail", Toast.LENGTH_SHORT).show();
                    break;
                case REQUEST_RESET:
                    Toast.makeText(getApplicationContext(), "Resetting Mailbuddy", Toast.LENGTH_SHORT).show();
                    break;
                case REQUEST_TOGGLE_HALL_READ:
                    Toast.makeText(getApplicationContext(), "Hall reading toggled", Toast.LENGTH_SHORT);
                    break;
                case REQUEST_TOGGLE_LED:
                    Toast.makeText(getApplicationContext(), "LED toggled", Toast.LENGTH_SHORT).show();
                    break;
                case REQUEST_START_MONITORING:
                    Toast.makeText(getApplicationContext(), "Mailbuddy is now observing :)", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(getApplicationContext(), "Sending data: " + request, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Mailbuddy not connected", Toast.LENGTH_SHORT).show();
        }
    }

    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                BTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                BTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connection with BT device using UUID
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            StringBuilder readMessage = new StringBuilder();
            while (true) {
                try {
                    // считываю входящие данные из потока и собираю в строку ответа
                    bytes = mmInStream.read(buffer);
                    String readed = new String(buffer, 0, bytes);
                    readMessage.append(readed);

                    // маркер конца команды - вернуть ответ в главный поток
                    if (readed.contains("\n")) {
                        messageHandler.obtainMessage(MESSAGE_READ, bytes, -1, readMessage.toString()).sendToTarget();
                        readMessage.setLength(0);
                    }

                } catch (IOException e) {
                    e.printStackTrace();

                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
}
