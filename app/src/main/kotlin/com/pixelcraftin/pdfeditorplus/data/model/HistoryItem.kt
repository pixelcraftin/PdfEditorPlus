package com.pixelcraftin.pdfeditorplus.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val toolName: String,
    val fileSize: Long,   // in bytes
    val timestamp: Long = System.currentTimeMillis(),
    val mimeType: String = "application/pdf"
)
