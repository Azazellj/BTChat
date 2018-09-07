package com.azazellj.demobluetooth

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.azazellj.recyclerview.adapter.kt.BaseAdapter
import com.azazellj.recyclerview.adapter.kt.common.AdapterViewHolder
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.Message
import com.google.android.gms.nearby.messages.MessageListener
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException


class MainActivity : AppCompatActivity() {

    companion object {
        private const val ID_DISCOVER = R.id.discover
        private const val ID_DISCOVER_STOP = R.id.discover_stop
        private const val ID_SHOW_PAIRED = R.id.show_paired_devices
        private const val ID_TEST_NEARBY = R.id.send_test_nearby
        private const val ID_BT_SERVER_START = R.id.start_bt_server
        private const val ID_BT_SERVER_STOP = R.id.stop_bt_server
        private const val ID_BT_CLIENT_STOP = R.id.stop_bt_client
        private val UUID = java.util.UUID.fromString("a70aec2c-19e4-4804-8e3c-557de4e3f558")

    }


    private val REQUEST_ENABLE_BT = 111
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var a2dp: BluetoothA2dp? = null
    private val handler: Handler = Handler(Looper.getMainLooper())

    private var serverThread: ServerThread? = null
    private var clientThread: ClientThread? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.A2DP) {
                a2dp = proxy as BluetoothA2dp
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                a2dp = null
            }
        }
    }

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                this@MainActivity.runOnUiThread {
                    adapter.add(device)
                }
            }
        }
    }


    val mMessageListener = object : MessageListener() {
        override fun onFound(message: Message) {
            val m = "namespace = " + message.namespace + "\n type = " + message.type + "\ncontent = " + String(message.content) + "\nfound\n"
            showMessage(m)
        }

        override fun onLost(message: Message) {
            val m = "namespace = " + message.namespace + "\n type = " + message.type + "\ncontent = " + String(message.content) + "\nlost\n"
            showMessage(m)
        }
    }

    val adapter = object : BaseAdapter<BluetoothDevice, AdapterViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdapterViewHolder {
            return AdapterViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_paired, parent, false))
        }

        override fun onBindViewHolder(holder: AdapterViewHolder, position: Int) {
            val device = getItem(holder.adapterPosition) ?: return

            holder.itemView.findViewById<AppCompatTextView>(R.id.name).text = "name = ${device.name}"
            holder.itemView.findViewById<AppCompatTextView>(R.id.address).text = "address = ${device.address}"
            holder.itemView.findViewById<AppCompatTextView>(R.id.bluetooth_class).text = " bluetooth class = " +
                    device.bluetoothClass.toString()

            holder.itemView.findViewById<AppCompatTextView>(R.id.uuids).text = "uuids = " +
                    device.uuids?.joinToString { it.uuid.toString() } ?: "null"

            holder.itemView.setOnClickListener {
                AlertDialog.Builder(it.context).setItems(arrayOf("Connect to server")) { _, which ->
                    when (which) {
                        0 -> startBTClient(device)
                    }
                }.show()
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(mReceiver, filter)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)

    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            ID_SHOW_PAIRED -> showPaired()
            ID_DISCOVER -> startDiscover()
            ID_DISCOVER_STOP -> stopDiscover()
            ID_TEST_NEARBY -> sendTestNearby()
            ID_BT_SERVER_START -> startBTServer()
            ID_BT_SERVER_STOP -> stopBTServer()
            ID_BT_CLIENT_STOP -> stopBTClient()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            init()
        }
    }

    override fun onResume() {
        super.onResume()
        init()
//        Nearby.getMessagesClient(this).subscribe(mMessageListener)

    }

    override fun onPause() {

        unregisterReceiver(mReceiver)
        Nearby.getMessagesClient(this).unsubscribe(mMessageListener)

        if (a2dp != null) {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, a2dp)
        }

        super.onPause()
    }

    private fun init() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//           val  mMessagesClient = Nearby.getMessagesClient(this, MessagesOptions.Builder()
//                    .setPermissions(NearbyPermissions.BLE)
//                    .build())
//            mMessagesClient.subscribe(mMessageListener)
//        }


        if (bluetoothAdapter == null) bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) return

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        bluetoothAdapter!!.getProfileProxy(this, profileListener, BluetoothProfile.A2DP)
    }

    private fun showPaired() {
        adapter.setItems(bluetoothAdapter?.bondedDevices?.toMutableList() ?: mutableListOf())
        recycler.adapter = adapter
    }

    private fun startDiscover() {
        adapter.clear()
        bluetoothAdapter!!.startDiscovery()


        handler.postDelayed({ adapter.notifyDataSetChanged() }, 5000)
    }

    private fun stopDiscover() {
        bluetoothAdapter?.cancelDiscovery()
    }

    private fun showMessage(message: String) {

//        val channel = NotificationChannel("notification", "Default", NotificationManager.IMPORTANCE_DEFAULT)
//        channel.setDescription(description)
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
//        val notificationManager = getSystemService(NotificationManager::class.java)
//        notificationManager.createNotificationChannel(channel)


        val n = NotificationCompat.Builder(this).setContentTitle("notification")
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        NotificationManagerCompat.from(this).notify(0, n.build())
    }

    private fun sendTestNearby() {
        Nearby.getMessagesClient(this).publish(Message("Hello World".toByteArray()))
    }

    private fun startBTServer() {
        serverThread = ServerThread(bluetoothAdapter).also { it.start() }
    }

    private fun stopBTServer() {
        serverThread?.cancel()
    }


    private class ServerThread(private val adapter: BluetoothAdapter?) : Thread() {
        private val mServerSocket: BluetoothServerSocket?

        init {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            var tmp: BluetoothServerSocket? = null
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = adapter?.listenUsingRfcommWithServiceRecord("Message server", UUID)
            } catch (e: IOException) {
                Log.e("ServerThread", "Socket's listen() method failed", e)
            }

            mServerSocket = tmp
        }

        override fun run() {
            var socket: BluetoothSocket? = null
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    socket = mServerSocket!!.accept()
                } catch (e: IOException) {
                    Log.e("ServerThread", "Socket's accept() method failed", e)
                    break
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    while (true) {
                        val mBuffer = ByteArray(1024)
                        val numBytes: Int // bytes returned from read()



                        try {
                            // Read from the InputStream.
                            numBytes = socket.inputStream.read(mBuffer)
                            val message = String(mBuffer)
                            val number = message.toInt()


                        } catch (e: IOException) {
                            Log.d("ServerThread", "Input stream was disconnected", e)

                        }
                    }
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                mServerSocket!!.close()
            } catch (e: IOException) {
                Log.e("ServerThread", "Could not close the connect socket", e)
            }
        }
    }

    private fun startBTClient(device: BluetoothDevice) {
        clientThread = ClientThread(bluetoothAdapter, device).also { it.start() }
    }

    private fun stopBTClient() {
        clientThread?.cancel()
    }


    private class ClientThread(private val bluetoothAdapter: BluetoothAdapter?, private val device: BluetoothDevice) : Thread() {
        private val mSocket: BluetoothSocket?

        init {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            var tmp: BluetoothSocket? = null
            try {

                tmp = device.createRfcommSocketToServiceRecord(UUID)
            } catch (e: IOException) {
                Log.e("ServerThread", "Socket's listen() method failed", e)
            }

            mSocket = tmp
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mSocket?.connect()
            } catch (connectException: IOException) {
                // Unable to connect; close the socket and return.
                try {
                    mSocket?.close()
                } catch (closeException: IOException) {
                    Log.e("ClientThread", "Could not close the client socket", closeException)
                }

                return
            }


            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
//            manageMyConnectedSocket(mSocket)
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                mSocket!!.close()
            } catch (e: IOException) {
                Log.e("ServerThread", "Could not close the connect socket", e)
            }
        }
    }
}
