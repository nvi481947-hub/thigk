package com.example.thigk.model

import com.google.firebase.firestore.DocumentId

data class Product(
    @DocumentId
    var id: String = "",
    var name: String = "",
    var type: String = "",
    var price: Long = 0,
    var image: String = ""
)
