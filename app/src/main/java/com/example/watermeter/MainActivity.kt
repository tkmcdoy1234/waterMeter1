package com.example.watermeter

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.watermeter.database.AppDatabase
import com.example.watermeter.database.OfflineReading
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "WaterMeterApp"
    private val webAppUrl = "https://script.google.com/macros/s/AKfycbyMtF4pb-2Cl_DZaEYMu-14piJMPtdeRJvwugwxR1yMni8x_34I4UEjeN0580SHwtAb/exec"
    
    private val BLUETOOTH_PERMISSION_REQ_CODE = 1001
    private val CAMERA_PERMISSION_REQ_CODE = 1002
    private val SMS_PERMISSION_REQ_CODE = 1003

    private val MINIMUM_CHARGE = 150.0 
    private val PER_CUBIC_RATE = 15.0 

    private var printerType: String = "NONE" 
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var selectedPrinter: BluetoothDevice? = null
    private val PRINTER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    
    private var printerIp: String? = null
    private var printerPort: Int = 9100
    private var currentReader: String = "admin"
    private var currentReceiptData: String? = null

    private lateinit var db: AppDatabase
    private lateinit var firestore: FirebaseFirestore
    private lateinit var txtOfflineCount: TextView
    private lateinit var btnSync: Button
    private lateinit var imgMeterPhoto: ImageView
    private var capturedPhotoBase64: String? = null

    private val takeOCRPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let { recognizeText(it) }
        }
    }

    private val takeVerificationPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let { 
                imgMeterPhoto.setImageBitmap(it)
                capturedPhotoBase64 = encodeImage(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()
        db = AppDatabase.getDatabase(this)
        firestore = FirebaseFirestore.getInstance()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val txtCurrentReader = findViewById<TextView>(R.id.txtCurrentReader)
        val editMeterId = findViewById<EditText>(R.id.editMeterId)
        val editReading = findViewById<EditText>(R.id.editReading)
        val txtPrevReading = findViewById<TextView>(R.id.txtPrevReading)
        val txtUsage = findViewById<TextView>(R.id.txtUsage)
        val txtAmount = findViewById<TextView>(R.id.txtAmount)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val btnPrint = findViewById<Button>(R.id.btnPrint)
        val txtStatus = findViewById<TextView>(R.id.txtStatus)
        val txtReceiptPreview = findViewById<TextView>(R.id.txtReceiptPreview)
        val btnSettings = findViewById<ImageView>(R.id.btnSettings)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnOCR = findViewById<ImageButton>(R.id.btnOCR)
        imgMeterPhoto = findViewById(R.id.imgMeterPhoto)
        txtOfflineCount = findViewById(R.id.txtOfflineCount)
        btnSync = findViewById(R.id.btnSync)

        val sharedPref = getSharedPreferences("MeterData", Context.MODE_PRIVATE)
        val loginPrefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        
        currentReader = loginPrefs.getString("currentUsername", "admin") ?: "admin"
        txtCurrentReader.text = "Reader: $currentReader"

        printerType = sharedPref.getString("printerType", "NONE") ?: "NONE"
        printerIp = sharedPref.getString("printerIp", null)
        printerPort = sharedPref.getInt("printerPort", 9100)
        
        updateOfflineStatus()

        btnSettings.setOnClickListener { showSettingsMenu() }

        btnOCR.setOnClickListener {
            checkCameraPermission {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                intent.putExtra("android.intent.extra.quickCapture", true)
                takeOCRPhotoLauncher.launch(intent)
            }
        }

        imgMeterPhoto.setOnClickListener {
            checkCameraPermission {
                takeVerificationPhotoLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            }
        }

        btnSync.setOnClickListener { syncOfflineData() }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes") { _, _ ->
                    loginPrefs.edit().putBoolean("isLoggedIn", false).apply()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("No", null)
                .show()
        }

        editMeterId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val meterId = s.toString().trim()
                val prevReading = sharedPref.getString("prev_$meterId", "0") ?: "0"
                val prevDate = sharedPref.getString("prev_date_$meterId", "No record") ?: "No record"
                txtPrevReading.text = "Previous Reading: $prevReading ($prevDate)"
                calculateUsage(editReading.text.toString(), prevReading, txtUsage, txtAmount)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        editReading.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val meterId = editMeterId.text.toString().trim()
                val prevReading = sharedPref.getString("prev_$meterId", "0") ?: "0"
                calculateUsage(s.toString(), prevReading, txtUsage, txtAmount)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSubmit.setOnClickListener {
            val meterId = editMeterId.text.toString().trim()
            val reading = editReading.text.toString().trim()
            val prevReading = sharedPref.getString("prev_$meterId", "0") ?: "0"
            val prevDate = sharedPref.getString("prev_date_$meterId", "N/A") ?: "N/A"
            val history = sharedPref.getString("history_$meterId", "") ?: ""

            if (meterId.isEmpty() || reading.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            } else if (capturedPhotoBase64 == null) {
                Toast.makeText(this, "Photo Verification Required", Toast.LENGTH_LONG).show()
            } else {
                validateAndSubmit(meterId, reading, prevReading, prevDate, history, btnSubmit, btnPrint, txtStatus, txtReceiptPreview, editMeterId, editReading, txtPrevReading, txtUsage, txtAmount)
            }
        }

        btnPrint.setOnClickListener {
            currentReceiptData?.let { data ->
                when (printerType) {
                    "BT" -> printViaBluetooth(data)
                    "WIFI" -> printViaWifi(data)
                    else -> Toast.makeText(this, "Please configure printer in Settings", Toast.LENGTH_LONG).show()
                }
            } ?: Toast.makeText(this, "No receipt data found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateAndSubmit(
        meterId: String, reading: String, prevReading: String, prevDate: String, history: String,
        btnSubmit: Button, btnPrint: Button, txtStatus: TextView, txtReceiptPreview: TextView,
        editMeterId: EditText, editReading: EditText, txtPrevReading: TextView, txtUsage: TextView, txtAmount: TextView
    ) {
        val rNew = reading.toDoubleOrNull() ?: 0.0
        val rPrev = prevReading.toDoubleOrNull() ?: 0.0
        
        if (rNew < rPrev) {
            AlertDialog.Builder(this)
                .setTitle("Correction Required")
                .setMessage("New reading ($rNew) is lower than previous ($rPrev). Please re-check the meter.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        txtStatus.text = "Checking account status..."
        btnSubmit.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val consumerDoc = firestore.collection("consumers").document(meterId).get().await()
                if (consumerDoc.exists()) {
                    val status = consumerDoc.getString("status") ?: "Connected"
                    if (status.equals("Disconnected", ignoreCase = true)) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Account Disconnected")
                            .setMessage("Reading cannot be performed because this Meter ID ($meterId) is currently DISCONNECTED.")
                            .setPositiveButton("OK", null)
                            .show()
                        btnSubmit.isEnabled = true
                        txtStatus.text = "Error: Account Disconnected"
                        return@launch
                    }
                }

                // Proceed with usage check
                val avgUsage = history.split(",")
                    .mapNotNull { it.split("|").firstOrNull()?.toDoubleOrNull() }
                    .average().let { if (it.isNaN()) 10.0 else it }

                val currentUsage = rNew - rPrev
                if (currentUsage > avgUsage * 2 && avgUsage > 5) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("High Usage Warning")
                        .setMessage("Current usage (${String.format("%.1f", currentUsage)}) is significantly higher than average (${String.format("%.1f", avgUsage)}). Proceed?")
                        .setPositiveButton("Yes") { _, _ -> 
                            performSubmission(meterId, reading, prevReading, prevDate, btnSubmit, btnPrint, txtStatus, txtReceiptPreview, editMeterId, editReading, txtPrevReading, txtUsage, txtAmount) 
                        }
                        .setNegativeButton("No") { _, _ -> 
                            btnSubmit.isEnabled = true
                            txtStatus.text = "Cancelled by user"
                        }
                        .show()
                } else {
                    performSubmission(meterId, reading, prevReading, prevDate, btnSubmit, btnPrint, txtStatus, txtReceiptPreview, editMeterId, editReading, txtPrevReading, txtUsage, txtAmount)
                }
            } catch (e: Exception) {
                // If offline, just proceed to submission (it will handle offline saving)
                performSubmission(meterId, reading, prevReading, prevDate, btnSubmit, btnPrint, txtStatus, txtReceiptPreview, editMeterId, editReading, txtPrevReading, txtUsage, txtAmount)
            }
        }
    }

    private fun performSubmission(
        meterId: String, reading: String, prevReading: String, prevDate: String,
        btnSubmit: Button, btnPrint: Button, txtStatus: TextView, txtReceiptPreview: TextView,
        editMeterId: EditText, editReading: EditText, txtPrevReading: TextView, txtUsage: TextView, txtAmount: TextView
    ) {
        val rNew = reading.toDoubleOrNull() ?: 0.0
        val rPrev = prevReading.toDoubleOrNull() ?: 0.0
        val usage = (rNew - rPrev).coerceAtLeast(0.0)
        val amount = (usage * PER_CUBIC_RATE).coerceAtLeast(MINIMUM_CHARGE)
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val periodCovered = if (prevDate != "No record" && prevDate != "N/A") "$prevDate to $date" else date

        val receiptData = """
            DAUIN WATER SYSTEM
            ------------------
            Date: $date
            Period: $periodCovered
            Reader: $currentReader
            Meter ID: $meterId
            Prev Reading: $rPrev
            Curr Reading: $rNew
            Usage: ${String.format("%.1f", usage)} m3
            Amount: P${String.format("%.2f", amount)}
            ------------------
            THANK YOU!
        """.trimIndent()

        currentReceiptData = receiptData
        txtReceiptPreview.text = receiptData
        txtReceiptPreview.visibility = View.VISIBLE
        btnPrint.isEnabled = true

        if (isOnline()) {
            txtStatus.text = "Submitting online..."
            submitToGoogleSheets(meterId, reading, amount.toString(), date, receiptData, btnSubmit, txtStatus, editMeterId, editReading, txtPrevReading, txtUsage, txtAmount)
            submitToFirestore(meterId, reading, rPrev.toString(), usage, amount, date, periodCovered)
        } else {
            saveOffline(meterId, reading, rPrev.toString(), usage, amount, date, receiptData, periodCovered)
            txtStatus.text = "Saved Offline (No Internet)"
            btnSubmit.isEnabled = true
            clearInputs(editMeterId, editReading, txtPrevReading, txtUsage, txtAmount)
        }
    }

    private fun submitToFirestore(meterId: String, reading: String, prevReading: String, usage: Double, amount: Double, date: String, period: String) {
        val billingData = hashMapOf(
            "meterId" to meterId,
            "reading" to reading,
            "prevReading" to prevReading,
            "usage" to usage,
            "amount" to amount,
            "date" to date,
            "periodCovered" to period,
            "status" to "Unpaid",
            "reader" to currentReader,
            "photo" to capturedPhotoBase64
        )

        firestore.collection("billings")
            .add(billingData)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully added to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding to Firestore", e)
            }
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
        return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showSettingsMenu() {
        val options = arrayOf("Bluetooth Printer", "WiFi Printer", "No Printer", "Clear Cache")
        AlertDialog.Builder(this)
            .setTitle("Printer Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setupBluetoothPrinter()
                    1 -> setupWifiPrinter()
                    2 -> {
                        getSharedPreferences("MeterData", Context.MODE_PRIVATE).edit().putString("printerType", "NONE").apply()
                        printerType = "NONE"
                    }
                    3 -> clearCache()
                }
            }
            .show()
    }

    private fun setupBluetoothPrinter() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), BLUETOOTH_PERMISSION_REQ_CODE)
            return
        }

        val pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
        val deviceNames = pairedDevices.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Printer")
            .setItems(deviceNames) { _, which ->
                selectedPrinter = pairedDevices.elementAt(which)
                getSharedPreferences("MeterData", Context.MODE_PRIVATE).edit().putString("printerType", "BT").apply()
                printerType = "BT"
                Toast.makeText(this, "Printer Selected: ${selectedPrinter?.name}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setupWifiPrinter() {
        val layout = LayoutInflater.from(this).inflate(R.layout.dialog_wifi_setup, null)
        val editIp = layout.findViewById<EditText>(R.id.editIpAddress)
        val editPort = layout.findViewById<EditText>(R.id.editPort)
        
        editIp.setText(printerIp ?: "192.168.1.100")
        editPort.setText(printerPort.toString())

        AlertDialog.Builder(this)
            .setTitle("WiFi Printer Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                printerIp = editIp.text.toString()
                printerPort = editPort.text.toString().toIntOrNull() ?: 9100
                getSharedPreferences("MeterData", Context.MODE_PRIVATE).edit()
                    .putString("printerIp", printerIp)
                    .putInt("printerPort", printerPort)
                    .putString("printerType", "WIFI")
                    .apply()
                printerType = "WIFI"
                Toast.makeText(this, "WiFi Printer Saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearCache() {
        AlertDialog.Builder(this)
            .setTitle("Clear Cache")
            .setMessage("This will delete all previous readings and history stored on this device. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                getSharedPreferences("MeterData", Context.MODE_PRIVATE).edit().clear().apply()
                CoroutineScope(Dispatchers.IO).launch {
                    db.offlineReadingDao().deleteAllReadings()
                    withContext(Dispatchers.Main) {
                        updateOfflineStatus()
                        Toast.makeText(this@MainActivity, "Cache Cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun calculateUsage(current: String, previous: String, txtUsage: TextView, txtAmount: TextView) {
        val rNew = current.toDoubleOrNull() ?: 0.0
        val rPrev = previous.toDoubleOrNull() ?: 0.0
        val usage = (rNew - rPrev).coerceAtLeast(0.0)
        val amount = (usage * PER_CUBIC_RATE).coerceAtLeast(MINIMUM_CHARGE)
        
        txtUsage.text = "Usage: ${String.format("%.1f", usage)} m3"
        txtAmount.text = "Estimated: P${String.format("%.2f", amount)}"
    }

    private fun recognizeText(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val resultText = visionText.text
                val numbers = "\\d+\\.?\\d*".toRegex().findAll(resultText).map { it.value }.toList()
                if (numbers.isNotEmpty()) {
                    findViewById<EditText>(R.id.editReading).setText(numbers.last())
                }
            }
    }

    private fun encodeImage(bm: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val b = baos.toByteArray()
        return Base64.encodeToString(b, Base64.DEFAULT)
    }

    private fun submitToGoogleSheets(
        meterId: String, reading: String, amount: String, date: String, receipt: String,
        btnSubmit: Button, txtStatus: TextView,
        editMeterId: EditText, editReading: EditText, txtPrevReading: TextView, txtUsage: TextView, txtAmount: TextView
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(webAppUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                
                val postData = "meterId=${URLEncoder.encode(meterId, "UTF-8")}" +
                        "&reading=${URLEncoder.encode(reading, "UTF-8")}" +
                        "&amount=${URLEncoder.encode(amount, "UTF-8")}" +
                        "&date=${URLEncoder.encode(date, "UTF-8")}" +
                        "&reader=${URLEncoder.encode(currentReader, "UTF-8")}" +
                        "&photo=${URLEncoder.encode(capturedPhotoBase64 ?: "", "UTF-8")}"

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(postData)
                writer.flush()

                val responseCode = conn.responseCode
                withContext(Dispatchers.Main) {
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(this@MainActivity, "Submitted Successfully!", Toast.LENGTH_SHORT).show()
                        txtStatus.text = "Status: Online - Submitted"
                        
                        // Save to local history
                        val sharedPref = getSharedPreferences("MeterData", Context.MODE_PRIVATE)
                        val history = sharedPref.getString("history_$meterId", "") ?: ""
                        val newHistory = if (history.isEmpty()) "$reading|$date" else "$reading|$date,$history"
                        
                        sharedPref.edit()
                            .putString("prev_$meterId", reading)
                            .putString("prev_date_$meterId", date)
                            .putString("history_$meterId", newHistory.take(200)) // Keep last few
                            .apply()

                        clearInputs(editMeterId, editReading, txtPrevReading, txtUsage, txtAmount)
                    } else {
                        txtStatus.text = "Error: $responseCode. Saved Offline."
                        val sharedPrefLocal = getSharedPreferences("MeterData", Context.MODE_PRIVATE)
                        val usage = (reading.toDoubleOrNull() ?: 0.0) - (sharedPrefLocal.getString("prev_$meterId", "0")?.toDoubleOrNull() ?: 0.0)
                        saveOffline(meterId, reading, sharedPrefLocal.getString("prev_$meterId", "0") ?: "0", usage, amount.toDoubleOrNull() ?: 0.0, date, receipt, "")
                    }
                    btnSubmit.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    txtStatus.text = "Network Error. Saved Offline."
                    val sharedPrefLocal = getSharedPreferences("MeterData", Context.MODE_PRIVATE)
                    val usage = (reading.toDoubleOrNull() ?: 0.0) - (sharedPrefLocal.getString("prev_$meterId", "0")?.toDoubleOrNull() ?: 0.0)
                    saveOffline(meterId, reading, sharedPrefLocal.getString("prev_$meterId", "0") ?: "0", usage, amount.toDoubleOrNull() ?: 0.0, date, receipt, "")
                    btnSubmit.isEnabled = true
                }
            }
        }
    }

    private fun saveOffline(meterId: String, reading: String, prevReading: String, usage: Double, amount: Double, date: String, receipt: String, period: String) {
        CoroutineScope(Dispatchers.IO).launch {
            db.offlineReadingDao().insertReading(
                OfflineReading(
                    meterId = meterId,
                    reading = reading,
                    usage = usage,
                    amount = amount,
                    timestamp = date,
                    reader = currentReader,
                    photoBase64 = capturedPhotoBase64,
                    receiptText = receipt
                )
            )
            withContext(Dispatchers.Main) {
                updateOfflineStatus()
                notifyOfflineSaving()
            }
        }
    }

    private fun updateOfflineStatus() {
        CoroutineScope(Dispatchers.IO).launch {
            val count = db.offlineReadingDao().getReadingCount()
            withContext(Dispatchers.Main) {
                txtOfflineCount.text = "Offline Records: $count"
                btnSync.visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        }
    }

    private fun syncOfflineData() {
        if (!isOnline()) {
            Toast.makeText(this, "Internet connection required for sync", Toast.LENGTH_SHORT).show()
            return
        }

        btnSync.isEnabled = false
        Toast.makeText(this, "Syncing data...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            val readings = db.offlineReadingDao().getAllReadings()
            var successCount = 0

            for (r in readings) {
                try {
                    val url = URL(webAppUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    val postData = "meterId=${URLEncoder.encode(r.meterId, "UTF-8")}" +
                            "&reading=${URLEncoder.encode(r.reading, "UTF-8")}" +
                            "&amount=${URLEncoder.encode(r.amount.toString(), "UTF-8")}" +
                            "&date=${URLEncoder.encode(r.timestamp, "UTF-8")}" +
                            "&reader=${URLEncoder.encode(r.reader, "UTF-8")}" +
                            "&photo=${URLEncoder.encode(r.photoBase64 ?: "", "UTF-8")}"

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(postData)
                    writer.flush()

                    if (conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP || conn.responseCode == HttpURLConnection.HTTP_OK) {
                        // For sync, we might need to reconstruct prevReading if not in Room
                        val prevR = (r.reading.toDoubleOrNull() ?: 0.0) - r.usage
                        submitToFirestore(r.meterId, r.reading, prevR.toString(), r.usage, r.amount, r.timestamp, "")
                        db.offlineReadingDao().deleteReading(r)
                        successCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed for ${r.meterId}: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                btnSync.isEnabled = true
                updateOfflineStatus()
                Toast.makeText(this@MainActivity, "Synced $successCount records", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun printViaBluetooth(data: String) {
        if (selectedPrinter == null) {
            Toast.makeText(this, "No printer selected", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var socket: BluetoothSocket? = null
            try {
                socket = selectedPrinter?.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket?.connect()
                val outputStream = socket?.outputStream
                outputStream?.write(data.toByteArray())
                outputStream?.write("\n\n\n".toByteArray())
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Printing...", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Print failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                socket?.close()
            }
        }
    }

    private fun printViaWifi(data: String) {
        if (printerIp == null) {
            Toast.makeText(this, "WiFi Printer IP not set", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(printerIp, printerPort), 5000)
                val outputStream = socket.getOutputStream()
                outputStream.write(data.toByteArray())
                outputStream.write("\n\n\n".toByteArray())
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Printing via WiFi...", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "WiFi Print failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                socket?.close()
            }
        }
    }

    private fun clearInputs(editMeterId: EditText, editReading: EditText, txtPrevReading: TextView, txtUsage: TextView, txtAmount: TextView) {
        editMeterId.text.clear()
        editReading.text.clear()
        txtPrevReading.text = "Previous Reading: 0"
        txtUsage.text = "Usage: 0.0 m3"
        txtAmount.text = "Estimated: P0.00"
        imgMeterPhoto.setImageResource(android.R.drawable.ic_menu_camera)
        capturedPhotoBase64 = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Water Meter Notifications"
            val descriptionText = "Notifications for offline sync and status"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("WATER_METER_CHAN", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun notifyOfflineSaving() {
        val builder = NotificationCompat.Builder(this, "WATER_METER_CHAN")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Reading Saved Offline")
            .setContentText("Your reading has been saved locally and will sync when internet is available.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(1, builder.build())
            }
        }
    }

    private fun checkCameraPermission(onGranted: () -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQ_CODE)
        } else {
            onGranted()
        }
    }
}
