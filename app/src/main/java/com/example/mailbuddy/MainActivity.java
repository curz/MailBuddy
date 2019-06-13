package com.example.mailbuddy;

import android.annotation.SuppressLint;
import android.os.Message;
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

    //Arduino requests
    String REQUEST_MAIL_STATUS = "1";
    String REQUEST_START_HALL_READ = "2";
    String REQUEST_STOP_HALL_READ = "3";
    String REQUEST_RESET = "4";
    String REQUEST_TOGGLE_LED = "5";

    //GUI components
    private TextView gotMail, getGotMail;
    private TextView sensorReading, getSensorReading;
    private TextView mailBuddyConnected, getMailBuddyConnected;
    private TextView generalOutput;
    private Button check;
    private Button connect;
    private Button startReadingSensor;
    private Button stopReadingSensor;

    private Button showPairedDevices;
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


    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mailBuddyConnected = (TextView) findViewById(R.id.mailBuddyConnected);
        getMailBuddyConnected = (TextView) findViewById(R.id.mailBuddyConnectedPlaceholder);
        sensorReading = (TextView) findViewById(R.id.sensorReading);
        getSensorReading = (TextView) findViewById(R.id.sensorReadingPlaceholder);
        gotMail = (TextView) findViewById(R.id.gotMail);
        getGotMail = (TextView) findViewById(R.id.gotMailPlaceholder);

        generalOutput = (TextView) findViewById(R.id.generalOutput);

        check = (Button) findViewById(R.id.check);
        connect = (Button) findViewById(R.id.showPairedDevices);
        startReadingSensor = (Button) findViewById(R.id.startReadingSensor);
        stopReadingSensor = (Button) findViewById(R.id.stopReadingSensor);

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

                getMailBuddyConnected.setText("Connecting...");
                // Get the device MAC address, which is the last 17 chars in the View
                final String address = ((MyAdapter) devicesRecyclerView.getAdapter()).getDevice(position).getDesc();
                final String name = ((MyAdapter) devicesRecyclerView.getAdapter()).getDevice(position).getHeader();

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
                                    if(messageContent.equals("1")){
                                        getGotMail.setText(R.string.new_mail);
                                    } else {
                                        getGotMail.setText(R.string.no_new_mail);
                                    }
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
                        getMailBuddyConnected.setText("Connected to device " + (String) (msg.obj));
                    } else {
                        getMailBuddyConnected.setText("Connection failed");
                    }
                }
            }
        };

        if (BTArrayAdapter == null) {
            // Device does not support Bluetooth
            generalOutput.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(), "Bluetooth device not found!", Toast.LENGTH_SHORT).show();
        } else {
            //Check button
            check.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkForMail(v);
//                    toggleLed(v);
                }
            });

            connect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //discover(v);
                    listPairedDevices(v);
                }
            });

            startReadingSensor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startReadingSensor();
                }
            });

            stopReadingSensor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopReadingSensor();
                }
            });
        }
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

    private void sendValue(View v, String value) {
        connectedThread.write(value);
    }

    private void requestMailStatus(View v) {
        connectedThread.write(REQUEST_MAIL_STATUS);
    }

    private void toggleLed(View view) {
        connectedThread.write(REQUEST_TOGGLE_LED);
    }

    private void startReadingSensor() {
        connectedThread.write(REQUEST_START_HALL_READ);
    }

    private void stopReadingSensor() {
        connectedThread.write(REQUEST_STOP_HALL_READ);
    }

    private void checkForMail(View v) {
        connectedThread.write(REQUEST_MAIL_STATUS);
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

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            if (!BTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                return;
            }

            mailBuddyConnected.setText("Connecting...");
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0, info.length() - 17);

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
                        connectedThread = new ConnectedThread(BTSocket);
                        connectedThread.start();

                        messageHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                .sendToTarget();
                    }
                }
            }.start();
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
