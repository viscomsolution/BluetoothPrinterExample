package com.example.bluetoothprinterexample;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // will show the statuses like bluetooth open, close or data sent
    TextView myLabel;

    // will enable user to enter any text to be printed
    EditText myTextbox;

    ListView lvDevice;

    // android built in classes for bluetooth operations
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;

    // needed for communication to bluetooth device / network
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;

    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    final ArrayList<CCheckpoint> m_listContent = new ArrayList<>();
    CChecklistAdapter m_adaptCheckList;
    Set<BluetoothDevice> m_pairedDevices;

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lvDevice = findViewById(R.id.lvDevice);

        // text label and input box
        myLabel = findViewById(R.id.label);
        myTextbox = findViewById(R.id.entry);

        m_adaptCheckList = new CChecklistAdapter(this, m_listContent);

        lvDevice.setAdapter(m_adaptCheckList);

        lvDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                if(m_pairedDevices.size() > 0)
                {
                    String deviceName = m_listContent.get(position).m_deviceName;
                    m_listContent.get(position).m_status = CCheckpoint.CONNECTED;
                    for (BluetoothDevice device : m_pairedDevices) {
                        if(device.getName().equals(deviceName)) {
                            mmDevice = device;
                            m_adaptCheckList.notifyDataSetChanged();

                            openBT();
                            break;
                        }
                    }
                }
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onResume() {
        super.onResume();
        findBT();

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this will find a bluetooth printer device
    void findBT() {

        try {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            if(mBluetoothAdapter == null) {
                myLabel.setText("No bluetooth adapter available");
            }

            if(!mBluetoothAdapter.isEnabled()) {
                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetooth, 0);
            }

            m_pairedDevices = mBluetoothAdapter.getBondedDevices();

            if(m_pairedDevices.size() > 0) {
                m_listContent.clear();
                for (BluetoothDevice device : m_pairedDevices) {

                    // RPP300 is the name of the bluetooth printer device
                    // we got this name from the list of paired devices

                    String deviceName = device.getName();
                    m_listContent.add(new CCheckpoint(deviceName, device.getAddress(), CCheckpoint.UNCONNECT));

                }
                m_adaptCheckList.notifyDataSetChanged();
            }

            //myLabel.setText("Bluetooth device found.");

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // tries to open a connection to the bluetooth printer device
    void openBT() {
        try {

            // Standard SerialPortService ID
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();

            beginListenForData();

            myLabel.setText("Bluetooth Opened");

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /*
     * after opening a connection to bluetooth printer device,
     * we have to listen and check if a data were sent to be printed.
     */
    void beginListenForData() {
        try {
            final Handler handler = new Handler();

            // this is the ASCII code for a newline character
            final byte delimiter = 10;

            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];

            workerThread = new Thread(new Runnable() {
                public void run() {

                    while (!Thread.currentThread().isInterrupted() && !stopWorker) {

                        try {

                            int bytesAvailable = mmInputStream.available();

                            if (bytesAvailable > 0) {

                                byte[] packetBytes = new byte[bytesAvailable];
                                mmInputStream.read(packetBytes);

                                for (int i = 0; i < bytesAvailable; i++) {

                                    byte b = packetBytes[i];
                                    if (b == delimiter) {

                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(
                                                readBuffer, 0,
                                                encodedBytes, 0,
                                                encodedBytes.length
                                        );

                                        // specify US-ASCII encoding
                                        final String data = new String(encodedBytes, "US-ASCII");
                                        readBufferPosition = 0;

                                        // tell the user data were sent to bluetooth printer device
                                        handler.post(new Runnable() {
                                            public void run() {
                                                myLabel.setText(data);
                                            }
                                        });

                                    } else {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                            }

                        } catch (IOException ex) {
                            stopWorker = true;
                        }

                    }
                }
            });

            workerThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void btn_send_onclick(View view) {
        try {
            sendData();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this will send text data to be printed by the bluetooth printer
    void sendData() throws IOException {
        try {

            // the text typed by the user
            String msg = myTextbox.getText().toString();
            msg += "\n\n\n";

            mmOutputStream.write(msg.getBytes());

            // tell the user data were sent
            myLabel.setText("Data sent.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void btn_close_onclick(View view) {
        try {
            closeBT();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // close the connection to bluetooth printer.
    void closeBT() throws IOException {
        try {
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
            myLabel.setText("Bluetooth Closed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class CCheckpoint {

        static final int UNCONNECT = 0;
        static final int CONNECTED = 1;

        String m_deviceName;
        String m_checkPointID;
        int m_status;

        public CCheckpoint(String name, String ID, int status)
        {
            this.m_deviceName = name;
            this.m_checkPointID = ID;
            this.m_status = status;
        }

        public String getCheckPointName() {
            return m_deviceName;
        }

        public String getCheckPointID() {
            return m_checkPointID;
        }

        public int getStatus() {
            return m_status;
        }

        public void setStatus(int status) {
            m_status = status;
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class CChecklistAdapter extends ArrayAdapter<CCheckpoint> {
        private final Context context;
        private final ArrayList<CCheckpoint> itemsArrayList;

        public CChecklistAdapter(@NonNull Context context, ArrayList<CCheckpoint> itemsArrayList) {
            super(context, R.layout.list_item_with_icon, itemsArrayList);

            this.context = context;
            this.itemsArrayList = itemsArrayList;
        }

        @Override
        public CCheckpoint getItem(int position) {
            return itemsArrayList.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView = inflater.inflate(R.layout.list_item_with_icon, parent, false);

            ImageView iconStatus = rowView.findViewById(R.id.imgIcon);
            TextView checkPointName = rowView.findViewById(R.id.txtCheckPointName);

            switch (itemsArrayList.get(position).getStatus()) {
                case CCheckpoint.UNCONNECT:
                    iconStatus.setImageResource(R.drawable.check_blank);
                    break;
                case CCheckpoint.CONNECTED:
                    iconStatus.setImageResource(R.drawable.check_mark);
                    break;
            }
            checkPointName.setText(itemsArrayList.get(position).getCheckPointName());

            return rowView;
        }
    }

}
