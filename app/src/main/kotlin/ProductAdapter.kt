package com.example.thigk.model

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.thigk.R
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private val list: List<Product>,
    private val isAdmin: Boolean,
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgProduct)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvPrice: TextView = v.findViewById(R.id.tvPrice)
        val tvType: TextView = v.findViewById(R.id.tvType)
        val btnEdit: ImageButton = v.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return VH(v)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = list[position]
        h.tvName.text = "Tên: ${p.name}"

        val formatter = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"))
        val formattedPrice = formatter.format(p.price)
        h.tvPrice.text = "Giá: $formattedPrice VNĐ"
        h.tvType.text = "Loại: ${p.type}"

        // Hiển thị ảnh từ chuỗi Base64 an toàn
        if (p.image.isNotEmpty()) {
            try {
                val bytes = Base64.decode(p.image, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    h.img.setImageBitmap(bmp)
                } else {
                    h.img.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } catch (_: Exception) {
                h.img.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            h.img.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // Phân quyền: user thường không thấy nút sửa/xóa
        val vis = if (isAdmin) View.VISIBLE else View.GONE
        h.btnEdit.visibility = vis
        h.btnDelete.visibility = vis
        h.btnEdit.setOnClickListener { onEdit(p) }
        h.btnDelete.setOnClickListener { onDelete(p) }
    }
}