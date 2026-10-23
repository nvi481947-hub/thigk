package com.example.thigk.model

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.thigk.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class ProductActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val list = mutableListOf<Product>()
    private lateinit var adapter: ProductAdapter

    private lateinit var edtName: EditText
    private lateinit var edtType: EditText
    private lateinit var edtPrice: EditText
    private lateinit var txtImage: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private var isAdmin = false
    private var imageBase64 = ""        // ảnh vừa chọn
    private var editingId: String? = null // null = đang thêm mới, khác null = đang sửa

    // Hộp chọn ảnh từ thư viện
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                if (bmp != null && (bmp.width > 0) && (bmp.height > 0)) {
                    val w = 300
                    val h = (bmp.height * w / bmp.width).coerceAtLeast(1) // thu nhỏ cho nhẹ
                    val small = Bitmap.createScaledBitmap(bmp, w, h, true)
                    val out = ByteArrayOutputStream()
                    small.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    imageBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    txtImage.text = "Đã chọn: ${uri.lastPathSegment ?: "Hình ảnh"}"
                } else {
                    Toast.makeText(this, "Không thể đọc file ảnh", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "Lỗi đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product)

        isAdmin = intent.getBooleanExtra("isAdmin", false)
        edtName = findViewById(R.id.edtName)
        edtType = findViewById(R.id.edtType)
        edtPrice = findViewById(R.id.edtPrice)
        txtImage = findViewById(R.id.txtImage)
        txtStatus = findViewById(R.id.txtStatus)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        // Phân quyền: user thường không thấy form nhập
        if (!isAdmin) {
            findViewById<View>(R.id.formLayout).visibility = View.GONE
        }

        val rv = findViewById<RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ProductAdapter(
            list, isAdmin,
            onEdit = { startEdit(it) },
            onDelete = { confirmDelete(it) }
        )
        rv.adapter = adapter

        findViewById<Button>(R.id.btnPickImage).setOnClickListener { picker.launch("image/*") }
        btnSave.setOnClickListener { save() }
        btnCancel.setOnClickListener { clearForm() }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        loadProducts()
    }

    // HIỂN THỊ: tự cập nhật mỗi khi dữ liệu trên Firestore thay đổi
    private fun loadProducts() {
        db.collection("products").addSnapshotListener { snap, error ->
            if (error != null) {
                val msg = when {
                    error.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ->
                        "Lỗi quyền truy cập (PERMISSION_DENIED).\nVui lòng bật quyền Read/Write trong Firebase Console -> Firestore Rules."
                    error.message?.contains("NOT_FOUND", ignoreCase = true) == true ->
                        "Chưa khởi tạo Firestore Database trên Firebase Console."
                    else -> "Lỗi tải dữ liệu: ${error.message}"
                }
                txtStatus.text = msg
                txtStatus.setTextColor(android.graphics.Color.RED)
                txtStatus.visibility = View.VISIBLE
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            list.clear()
            for (d in snap.documents) {
                val rawPrice = d.get("price")
                val priceLong = when (rawPrice) {
                    is Long -> rawPrice
                    is Double -> rawPrice.toLong()
                    is String -> rawPrice.toLongOrNull() ?: 0L
                    is Number -> rawPrice.toLong()
                    else -> 0L
                }

                list.add(
                    Product(
                        id = d.id,
                        name = d.get("name")?.toString() ?: "",
                        type = d.get("type")?.toString() ?: "",
                        price = priceLong,
                        image = d.get("image")?.toString() ?: ""
                    )
                )
            }
            adapter.notifyDataSetChanged()

            if (list.isEmpty()) {
                txtStatus.text = "Chưa có sản phẩm nào trong cơ sở dữ liệu."
                txtStatus.setTextColor(android.graphics.Color.GRAY)
                txtStatus.visibility = View.VISIBLE
            } else {
                txtStatus.visibility = View.GONE
            }
        }
    }

    // THÊM hoặc SỬA
    private fun save() {
        val name = edtName.text.toString().trim()
        val type = edtType.text.toString().trim()
        val priceStr = edtPrice.text.toString().trim()

        if (name.isEmpty() || type.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            return
        }

        val price = priceStr.toLongOrNull()
        if (price == null || price < 0) {
            Toast.makeText(this, "Giá sản phẩm phải là số hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf<String, Any>(
            "name" to name,
            "type" to type,
            "price" to price
        )

        val id = editingId
        if (id == null) { // Thêm mới
            data["image"] = imageBase64
            db.collection("products").add(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Đã thêm sản phẩm thành công", Toast.LENGTH_SHORT).show()
                    clearForm()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Lỗi thêm sản phẩm: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else { // Sửa
            if (imageBase64.isNotEmpty()) {
                data["image"] = imageBase64 // không chọn ảnh mới thì giữ ảnh cũ
            }
            db.collection("products").document(id).update(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Đã cập nhật sản phẩm thành công", Toast.LENGTH_SHORT).show()
                    clearForm()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Lỗi cập nhật sản phẩm: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun startEdit(p: Product) {
        editingId = p.id
        edtName.setText(p.name)
        edtType.setText(p.type)
        edtPrice.setText(p.price.toString())
        imageBase64 = ""
        txtImage.text = "Giữ ảnh cũ (hoặc chọn ảnh mới)"
        btnSave.text = "CẬP NHẬT SẢN PHẨM"
        btnCancel.visibility = View.VISIBLE
    }

    // XÓA (có hỏi xác nhận)
    private fun confirmDelete(p: Product) {
        AlertDialog.Builder(this)
            .setTitle("Xóa sản phẩm")
            .setMessage("Bạn có chắc chắn muốn xóa \"${p.name}\"?")
            .setPositiveButton("Xóa") { _, _ ->
                db.collection("products").document(p.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Đã xóa sản phẩm", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Lỗi khi xóa: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun clearForm() {
        editingId = null
        imageBase64 = ""
        edtName.setText("")
        edtType.setText("")
        edtPrice.setText("")
        txtImage.text = "Chưa chọn ảnh"
        btnSave.text = "THÊM SẢN PHẨM"
        btnCancel.visibility = View.GONE
    }
}