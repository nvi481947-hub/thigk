package com.example.thigk.model

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thigk.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var edtEmail: EditText
    private lateinit var edtPass: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        edtEmail = findViewById(R.id.edtEmail)
        edtPass = findViewById(R.id.edtPass)

        findViewById<Button>(R.id.btnLogin).setOnClickListener { login() }
        findViewById<Button>(R.id.btnRegister).setOnClickListener { register() }
    }

    override fun onStart() {
        super.onStart()
        // Đã đăng nhập từ trước thì vào thẳng
        if (auth.currentUser != null) goByRole()
    }

    private fun login() {
        val email = edtEmail.text.toString().trim()
        val pass = edtPass.text.toString().trim()
        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Nhập đầy đủ email và mật khẩu", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { goByRole() }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Đăng nhập thất bại: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun register() {
        val email = edtEmail.text.toString().trim()
        val pass = edtPass.text.toString().trim()
        if (email.isEmpty()) {
            Toast.makeText(this, "Email không được để trống", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }
        if (pass.length < 6) {
            Toast.makeText(this, "Mật khẩu tối thiểu 6 ký tự", Toast.LENGTH_SHORT).show()
            return
        }
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    val data = hashMapOf("role" to "user") // mặc định là user thường
                    db.collection("users").document(user.uid).set(data)
                        .addOnSuccessListener { goByRole() }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Lỗi lưu dữ liệu người dùng: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Đăng ký thất bại: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun goByRole() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.get("role")?.toString() ?: "user"
                val isAdmin = doc.exists() && (role == "admin")
                val intent = Intent(this, ProductActivity::class.java)
                intent.putExtra("isAdmin", isAdmin)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                val intent = Intent(this, ProductActivity::class.java)
                intent.putExtra("isAdmin", false)
                startActivity(intent)
                finish()
            }
    }
}
