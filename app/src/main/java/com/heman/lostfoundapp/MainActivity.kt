package com.heman.lostfoundapp

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.provider.BaseColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    val imageUri: String,
    val createdAt: String
)

class LostFoundDatabase(context: Context) :
    SQLiteOpenHelper(context, "lost_found.db", null, 1) {

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

class MainActivity : AppCompatActivity() {

    private lateinit var database: LostFoundDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: AdvertAdapter
    private var selectedFilter = "All"
    private var pendingImageUri: String = ""
    private var pendingPreview: ImageView? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = LostFoundDatabase(this)
        buildMainScreen()
        loadAdverts()
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
        controls.addView(filterSpinner)
        controls.addView(addButton)
        root.addView(controls)

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
        val locationInput = input("Location, e.g. Deakin library")

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

        form.addView(label("Type"))
        form.addView(typeSpinner)
        form.addView(label("Category"))
        form.addView(categorySpinner)
        form.addView(titleInput)
        form.addView(descriptionInput)
        form.addView(nameInput)
        form.addView(phoneInput)
        form.addView(locationInput)
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
