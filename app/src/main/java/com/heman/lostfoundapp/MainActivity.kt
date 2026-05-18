package com.heman.lostfoundapp

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.BaseColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val categories = listOf("Electronics", "Pets", "Wallets", "Keys", "Clothing", "Other")

data class Advert(
    val id: Long,
    val type: String,
    val title: String,
    val category: String,
    val description: String,
    val ownerName: String,
    val phone: String,
    val location: String,
    val latitude: Double,
    val longitude: Double,
    val imageUri: String,
    val createdAt: String
)

class LostFoundDatabase(context: Context) :
    SQLiteOpenHelper(context, "lost_found.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE adverts (
                ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                category TEXT NOT NULL,
                description TEXT NOT NULL,
                owner_name TEXT NOT NULL,
                phone TEXT NOT NULL,
                location TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                image_uri TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS adverts")
        onCreate(db)
    }

    fun insertAdvert(advert: Advert): Long {
        val values = ContentValues().apply {
            put("type", advert.type)
            put("title", advert.title)
            put("category", advert.category)
            put("description", advert.description)
            put("owner_name", advert.ownerName)
            put("phone", advert.phone)
            put("location", advert.location)
            put("latitude", advert.latitude)
            put("longitude", advert.longitude)
            put("image_uri", advert.imageUri)
            put("created_at", advert.createdAt)
        }
        return writableDatabase.insert("adverts", null, values)
    }

    fun getAdverts(categoryFilter: String): List<Advert> {
        val adverts = mutableListOf<Advert>()
        val useFilter = categoryFilter != "All"
        val cursor = readableDatabase.query(
            "adverts",
            null,
            if (useFilter) "category = ?" else null,
            if (useFilter) arrayOf(categoryFilter) else null,
            null,
            null,
            "${BaseColumns._ID} DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                adverts.add(
                    Advert(
                        id = it.getLong(it.getColumnIndexOrThrow(BaseColumns._ID)),
                        type = it.getString(it.getColumnIndexOrThrow("type")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        category = it.getString(it.getColumnIndexOrThrow("category")),
                        description = it.getString(it.getColumnIndexOrThrow("description")),
                        ownerName = it.getString(it.getColumnIndexOrThrow("owner_name")),
                        phone = it.getString(it.getColumnIndexOrThrow("phone")),
                        location = it.getString(it.getColumnIndexOrThrow("location")),
                        latitude = it.getDouble(it.getColumnIndexOrThrow("latitude")),
                        longitude = it.getDouble(it.getColumnIndexOrThrow("longitude")),
                        imageUri = it.getString(it.getColumnIndexOrThrow("image_uri")),
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return adverts
    }

    fun deleteAdvert(id: Long) {
        writableDatabase.delete("adverts", "${BaseColumns._ID} = ?", arrayOf(id.toString()))
    }
}

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var database: LostFoundDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: AdvertAdapter
    private lateinit var radiusInput: EditText
    private lateinit var locationClient: FusedLocationProviderClient
    private var selectedFilter = "All"
    private var pendingImageUri: String = ""
    private var pendingPreview: ImageView? = null
    private var pendingLocationInput: EditText? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var googleMap: GoogleMap? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            pendingImageUri = uri.toString()
            pendingPreview?.setImageURI(uri)
        }
    }

    private val placePicker: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val place = Autocomplete.getPlaceFromIntent(result.data!!)
                val latLng = place.latLng
                if (latLng != null) {
                    selectedLatitude = latLng.latitude
                    selectedLongitude = latLng.longitude
                    pendingLocationInput?.setText(place.address ?: place.name ?: "Selected location")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = LostFoundDatabase(this)
        locationClient = LocationServices.getFusedLocationProviderClient(this)

        val apiKey = getString(R.string.google_maps_key)
        if (!Places.isInitialized() && !apiKey.startsWith("PUT_")) {
            Places.initialize(applicationContext, apiKey)
        }

        buildMainScreen()
        loadAdverts()
        askForLocationPermission()
    }

    private fun buildMainScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 42, 32, 24)
            setBackgroundColor(0xFFF6F8F7.toInt())
        }

        val title = TextView(this).apply {
            text = "Lost and Found"
            textSize = 28f
            setTextColor(0xFF1D4D50.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Post lost or found items, filter by category, and remove adverts after the owner is found."
            textSize = 15f
            setTextColor(0xFF455A5C.toInt())
            setPadding(0, 8, 0, 18)
        }
        root.addView(subtitle)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val filterSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("All") + categories
            )
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFilter = parent?.getItemAtPosition(position).toString()
                loadAdverts()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val addButton = Button(this).apply {
            text = "Post"
            setOnClickListener { showPostDialog() }
        }
        val showMapButton = Button(this).apply {
            text = "Show on Map"
            setOnClickListener { showAdvertsOnMap() }
        }
        controls.addView(filterSpinner)
        controls.addView(addButton)
        controls.addView(showMapButton)
        root.addView(controls)

        radiusInput = input("Radius in km, example 5. Leave blank to show all.").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        root.addView(radiusInput)

        val currentLocationButton = Button(this).apply {
            text = "GET CURRENT LOCATION"
            setOnClickListener { getCurrentLocation(null) }
        }
        root.addView(currentLocationButton)

        val mapContainerId = View.generateViewId()
        val mapContainer = FrameLayout(this).apply {
            id = mapContainerId
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                650
            ).apply { setMargins(0, 14, 0, 14) }
        }
        root.addView(mapContainer)

        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(mapContainerId, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)

        emptyText = TextView(this).apply {
            text = "No adverts yet. Tap Post to add your first lost or found item."
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(0xFF607D80.toInt())
            setPadding(0, 40, 0, 40)
        }
        root.addView(emptyText)

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        adapter = AdvertAdapter(
            onClick = { showDetailsDialog(it) },
            onDelete = { deleteAdvert(it) }
        )
        recyclerView.adapter = adapter
        root.addView(recyclerView)

        setContentView(root)
    }

    private fun showPostDialog() {
        pendingImageUri = ""
        selectedLatitude = null
        selectedLongitude = null

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 10)
        }

        val typeSpinner = spinnerOf(listOf("Lost", "Found"))
        val categorySpinner = spinnerOf(categories)
        val titleInput = input("Item title, e.g. Black wallet")
        val descriptionInput = input("Description")
        val nameInput = input("Your name")
        val phoneInput = input("Phone number")
        val locationInput = input("Type location, e.g. Deakin library")

        pendingPreview = ImageView(this).apply {
            setBackgroundColor(0xFFE4ECEB.toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                300
            ).apply { setMargins(0, 16, 0, 10) }
        }

        val imageButton = Button(this).apply {
            text = "Choose Image"
            setOnClickListener { imagePicker.launch(arrayOf("image/*")) }
        }

        val searchLocationButton = Button(this).apply {
            text = "SEARCH TYPED LOCATION"
            setOnClickListener {
                pendingLocationInput = locationInput
                searchTypedLocation(locationInput)
            }
        }

        val autocompleteButton = Button(this).apply {
            text = "CHOOSE LOCATION FROM GOOGLE"
            setOnClickListener {
                pendingLocationInput = locationInput
                openPlaceSearch()
            }
        }

        val useCurrentLocationButton = Button(this).apply {
            text = "GET CURRENT LOCATION"
            setOnClickListener {
                pendingLocationInput = locationInput
                getCurrentLocation(locationInput)
            }
        }

        form.addView(label("Type"))
        form.addView(typeSpinner)
        form.addView(label("Category"))
        form.addView(categorySpinner)
        form.addView(titleInput)
        form.addView(descriptionInput)
        form.addView(nameInput)
        form.addView(phoneInput)
        form.addView(locationInput)
        form.addView(searchLocationButton)
        form.addView(autocompleteButton)
        form.addView(useCurrentLocationButton)
        form.addView(pendingPreview)
        form.addView(imageButton)

        AlertDialog.Builder(this)
            .setTitle("Create advert")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val valid = validateAndSave(
                            type = typeSpinner.selectedItem.toString(),
                            category = categorySpinner.selectedItem.toString(),
                            title = titleInput.text.toString(),
                            description = descriptionInput.text.toString(),
                            ownerName = nameInput.text.toString(),
                            phone = phoneInput.text.toString(),
                            location = locationInput.text.toString()
                        )
                        if (valid) dismiss()
                    }
                }
                show()
            }
    }

    private fun validateAndSave(
        type: String,
        category: String,
        title: String,
        description: String,
        ownerName: String,
        phone: String,
        location: String
    ): Boolean {
        if (
            title.isBlank() ||
            description.isBlank() ||
            ownerName.isBlank() ||
            phone.isBlank() ||
            location.isBlank()
        ) {
            toast("Please fill in all text fields.")
            return false
        }

        if (pendingImageUri.isBlank()) {
            toast("Please choose an image for the advert.")
            return false
        }

        if (selectedLatitude == null || selectedLongitude == null) {
            toast("Please choose a location or use current location.")
            return false
        }

        val now = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        database.insertAdvert(
            Advert(
                id = 0,
                type = type,
                title = title.trim(),
                category = category,
                description = description.trim(),
                ownerName = ownerName.trim(),
                phone = phone.trim(),
                location = location.trim(),
                latitude = selectedLatitude!!,
                longitude = selectedLongitude!!,
                imageUri = pendingImageUri,
                createdAt = now
            )
        )
        toast("Advert saved")
        loadAdverts()
        return true
    }

    private fun loadAdverts() {
        val adverts = database.getAdverts(selectedFilter)
        adapter.submitList(adverts)
        emptyText.visibility = if (adverts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDetailsDialog(advert: Advert) {
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 10)
        }

        val image = ImageView(this).apply {
            setImageURI(Uri.parse(advert.imageUri))
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                420
            )
        }

        details.addView(image)
        details.addView(detailText("${advert.type}: ${advert.title}", 20f, true))
        details.addView(detailText("Category: ${advert.category}"))
        details.addView(detailText("Posted: ${advert.createdAt}"))
        details.addView(detailText("Location: ${advert.location}"))
        details.addView(detailText("Contact: ${advert.ownerName} - ${advert.phone}"))
        details.addView(detailText("Description: ${advert.description}"))

        AlertDialog.Builder(this)
            .setTitle("Advert details")
            .setView(ScrollView(this).apply { addView(details) })
            .setPositiveButton("Remove advert") { _, _ -> deleteAdvert(advert) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun deleteAdvert(advert: Advert) {
        database.deleteAdvert(advert.id)
        toast("Advert removed")
        loadAdverts()
        showAdvertsOnMap()
    }

    private fun openPlaceSearch() {
        if (getString(R.string.google_maps_key).startsWith("PUT_")) {
            toast("Add your Google Maps API key in strings.xml first.")
            return
        }

        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
        placePicker.launch(intent)
    }

    private fun searchTypedLocation(locationInput: EditText) {
        val locationText = locationInput.text.toString().trim()
        if (locationText.isBlank()) {
            toast("Please type a location first.")
            return
        }

        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val results = geocoder.getFromLocationName(locationText, 1)
            val firstResult = results?.firstOrNull()

            if (firstResult == null) {
                useSimpleLocationFallback(locationText, locationInput)
                return
            }

            selectedLatitude = firstResult.latitude
            selectedLongitude = firstResult.longitude
            locationInput.setText(locationText)
            moveMap(firstResult.latitude, firstResult.longitude, locationText)
            toast("Location selected")
        } catch (error: Exception) {
            useSimpleLocationFallback(locationText, locationInput)
        }
    }

    private fun useSimpleLocationFallback(locationText: String, locationInput: EditText) {
        val lowerText = locationText.lowercase(Locale.getDefault())
        val point = when {
            lowerText.contains("deakin") || lowerText.contains("burwood") ->
                LatLng(-37.8476, 145.1149)
            lowerText.contains("flinders") ->
                LatLng(-37.8183, 144.9671)
            lowerText.contains("melbourne central") ->
                LatLng(-37.8100, 144.9626)
            lowerText.contains("southern cross") ->
                LatLng(-37.8184, 144.9525)
            lowerText.contains("waterfront") || lowerText.contains("geelong") ->
                LatLng(-38.1436, 144.3619)
            else -> null
        }

        if (point == null) {
            toast("Try: Deakin Burwood Library, Flinders Street, or Melbourne Central.")
            return
        }

        selectedLatitude = point.latitude
        selectedLongitude = point.longitude
        locationInput.setText(locationText)
        moveMap(point.latitude, point.longitude, locationText)
        toast("Location selected using simple search")
    }

    private fun askForLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                101
            )
        }
    }

    private fun getCurrentLocation(locationInput: EditText?) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askForLocationPermission()
            return
        }

        locationClient.lastLocation.addOnSuccessListener { location ->
            if (location == null) {
                toast("Location not ready. Please try again.")
                return@addOnSuccessListener
            }

            currentLatitude = location.latitude
            currentLongitude = location.longitude
            selectedLatitude = location.latitude
            selectedLongitude = location.longitude

            val locationText = "Current location: ${location.latitude}, ${location.longitude}"
            locationInput?.setText(locationText)
            moveMap(location.latitude, location.longitude, "My current location")
            toast("Current location selected")
        }
    }

    private fun showAdvertsOnMap() {
        val map = googleMap ?: return
        map.clear()

        val adverts = database.getAdverts(selectedFilter)
        val radiusKm = radiusInput.text.toString().toDoubleOrNull()
        var shownCount = 0

        for (advert in adverts) {
            if (radiusKm != null && currentLatitude != null && currentLongitude != null) {
                val result = FloatArray(1)
                Location.distanceBetween(
                    currentLatitude!!,
                    currentLongitude!!,
                    advert.latitude,
                    advert.longitude,
                    result
                )
                val distanceKm = result[0] / 1000.0
                if (distanceKm > radiusKm) {
                    continue
                }
            }

            val point = LatLng(advert.latitude, advert.longitude)
            map.addMarker(
                MarkerOptions()
                    .position(point)
                    .title("${advert.type}: ${advert.title}")
                    .snippet("${advert.category} - ${advert.phone}")
            )
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 12f))
            shownCount++
        }

        if (radiusKm != null && currentLatitude == null) {
            toast("Tap GET CURRENT LOCATION first for radius search.")
        } else {
            toast("Showing $shownCount advert(s) on the map.")
        }
    }

    private fun moveMap(latitude: Double, longitude: Double, title: String) {
        val map = googleMap ?: return
        val point = LatLng(latitude, longitude)
        map.addMarker(MarkerOptions().position(point).title(title))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 14f))
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val deakinBurwood = LatLng(-37.8476, 145.1149)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(deakinBurwood, 11f))
    }

    private fun spinnerOf(values: List<String>): Spinner {
        return Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                values
            )
        }
    }

    private fun input(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 16f
            setSingleLine(false)
        }
    }

    private fun label(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 14f
            setTextColor(0xFF1D4D50.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 0)
        }
    }

    private fun detailText(value: String, size: Float = 15f, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(0xFF263A3B.toInt())
            setPadding(0, 12, 0, 0)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

class AdvertAdapter(
    private val onClick: (Advert) -> Unit,
    private val onDelete: (Advert) -> Unit
) : RecyclerView.Adapter<AdvertAdapter.AdvertViewHolder>() {

    private val adverts = mutableListOf<Advert>()

    fun submitList(newAdverts: List<Advert>) {
        adverts.clear()
        adverts.addAll(newAdverts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdvertViewHolder {
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 20, 0, 20)
            gravity = Gravity.CENTER_VERTICAL
        }

        val image = ImageView(parent.context).apply {
            id = View.generateViewId()
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFFE4ECEB.toInt())
            layoutParams = LinearLayout.LayoutParams(170, 170)
        }

        val textColumn = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(22, 0, 12, 0)
        }

        val title = TextView(parent.context).apply {
            id = View.generateViewId()
            textSize = 18f
            setTextColor(0xFF183B3E.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val meta = TextView(parent.context).apply {
            id = View.generateViewId()
            textSize = 14f
            setTextColor(0xFF496264.toInt())
        }

        val location = TextView(parent.context).apply {
            id = View.generateViewId()
            textSize = 14f
            setTextColor(0xFF496264.toInt())
        }

        val delete = Button(parent.context).apply {
            id = View.generateViewId()
            text = "Remove"
        }

        textColumn.addView(title)
        textColumn.addView(meta)
        textColumn.addView(location)
        row.addView(image)
        row.addView(textColumn)
        row.addView(delete)
        return AdvertViewHolder(row, image, title, meta, location, delete)
    }

    override fun onBindViewHolder(holder: AdvertViewHolder, position: Int) {
        val advert = adverts[position]
        holder.image.setImageURI(Uri.parse(advert.imageUri))
        holder.title.text = "${advert.type}: ${advert.title}"
        holder.meta.text = "${advert.category} - ${advert.createdAt}"
        holder.location.text = advert.location
        holder.itemView.setOnClickListener { onClick(advert) }
        holder.deleteButton.setOnClickListener { onDelete(advert) }
    }

    override fun getItemCount(): Int = adverts.size

    class AdvertViewHolder(
        itemView: View,
        val image: ImageView,
        val title: TextView,
        val meta: TextView,
        val location: TextView,
        val deleteButton: Button
    ) : RecyclerView.ViewHolder(itemView)
}
