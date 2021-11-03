package com.tankiem.flutter.flutter_mp3

import java.io.Serializable

data class Song(
    val filePath: String,
    val fileName: String,
    val image: ByteArray?,
    val artist: String?,
    val album: String?,
    val title: String?,
): Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Song

        if (filePath != other.filePath) return false
        if (fileName != other.fileName) return false
        if (image != null) {
            if (other.image == null) return false
            if (!image.contentEquals(other.image)) return false
        } else if (other.image != null) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (title != other.title) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + (image?.contentHashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        return result
    }
}