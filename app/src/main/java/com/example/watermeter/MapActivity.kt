package com.example.watermeter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore
    private val TAG = "MapActivity"
    private val dauinCenter = LatLng(9.1911, 123.2683)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        firestore = FirebaseFirestore.getInstance()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true
        }

        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL

        // Initial move to center
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(dauinCenter, 15f))

        loadLiveMeterLocations()
    }

    private fun loadLiveMeterLocations() {
        val sharedPref = getSharedPreferences("MeterData", Context.MODE_PRIVATE)
        val doneMeters = sharedPref.getStringSet("done_meters", emptySet()) ?: emptySet()

        // Fetch billings ordered by date to get the most recent location
        firestore.collection("billings")
            .orderBy("date", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshots ->
                if (snapshots.isEmpty) {
                    Toast.makeText(this, "No meter locations found in database", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                mMap.clear() // Clear existing markers
                val meterPositions = mutableMapOf<String, LatLng>()

                // Group by meterId to keep only the LATEST location
                for (doc in snapshots.documents) {
                    val meterId = doc.getString("meterId") ?: continue
                    val locationStr = doc.getString("locationPin") ?: continue
                    
                    if (locationStr.contains(",") && locationStr != "No Location") {
                        try {
                            val parts = locationStr.split(",")
                            val lat = parts[0].trim().toDouble()
                            val lng = parts[1].trim().toDouble()
                            meterPositions[meterId] = LatLng(lat, lng)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing location for $meterId: $locationStr", e)
                        }
                    }
                }

                if (meterPositions.isEmpty()) {
                    Toast.makeText(this, "No valid GPS coordinates found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Add markers for all discovered meters
                for ((meterId, position) in meterPositions) {
                    val isDone = doneMeters.contains(meterId)
                    val markerColor = if (isDone) {
                        BitmapDescriptorFactory.HUE_GREEN
                    } else {
                        BitmapDescriptorFactory.HUE_RED
                    }

                    mMap.addMarker(MarkerOptions()
                        .position(position)
                        .title("Meter: $meterId")
                        .snippet(if (isDone) "Status: DONE" else "Status: PENDING")
                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor)))
                }

                Log.d(TAG, "Successfully mapped ${meterPositions.size} meters.")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore Load Failed", e)
                Toast.makeText(this, "Error connecting to cloud database", Toast.LENGTH_LONG).show()
            }
    }
}
