package com.lmxyapp.handygallery

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import java.io.File
import kotlin.math.abs

/**
 * Finds photos that look alike, in two ways that back each other up. Photos taken seconds apart in
 * the same folder at the same size are probably a burst; and photos whose fingerprints are almost
 * identical are probably copies of one another, wherever and whenever they were saved.
 *
 * A fingerprint is a difference hash: the photo shrunk to 9x8 grey pixels, one bit per pixel saying
 * whether it is brighter than its right-hand neighbour. Similar pictures differ in only a few bits.
 */
object Similar {
    private const val FILE = "similar_hashes"

    /** How far apart two shots may be taken and still count as one burst, in milliseconds. */
    private const val BURST_MS = 10_000L

    /** Fingerprint bits that may differ within a burst, where time and size already agree. */
    private const val BURST_BITS = 20

    /** Fingerprint bits that may differ between photos with nothing else in common. */
    private const val COPY_BITS = 4

    /** Fingerprints worked out on earlier runs, by [Photo.cacheKey]. */
    fun load(context: Context): Map<String, Long> {
        val map = HashMap<String, Long>()
        runCatching {
            File(context.filesDir, FILE).forEachLine { line ->
                val cut = line.indexOf(' ')
                if (cut > 0) line.substring(cut + 1).toLongOrNull()?.let { map[line.substring(0, cut)] = it }
            }
        }
        return map
    }

    fun save(context: Context, hashes: Map<String, Long>) {
        runCatching { File(context.filesDir, FILE).writeText(hashes.entries.joinToString("\n") { "${it.key} ${it.value}" }) }
    }

    /** The photo's fingerprint, made from its thumbnail; null if it can't be read. */
    fun fingerprint(context: Context, photo: Photo): Long? = runCatching {
        val thumb = context.contentResolver.loadThumbnail(photo.uri, Size(128, 128), null)
        // Pixels can't be read out of a hardware bitmap.
        val readable = if (thumb.config == Bitmap.Config.HARDWARE) thumb.copy(Bitmap.Config.ARGB_8888, false) else thumb
        val small = Bitmap.createScaledBitmap(readable, 9, 8, true)
        val pixels = IntArray(72)
        small.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        fun grey(c: Int) = (c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114
        var bits = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            bits = bits shl 1
            if (grey(pixels[y * 9 + x]) > grey(pixels[y * 9 + x + 1])) bits = bits or 1L
        }
        bits
    }.getOrNull()

    /**
     * Groups of [photos] that look alike, newest group first. Within a group the one most worth
     * keeping comes first: the most pixels, then the largest file.
     */
    fun groups(photos: List<Photo>, hashes: Map<String, Long>): List<List<Photo>> {
        val items = photos.filter { it.cacheKey in hashes }
        val n = items.size
        val hash = LongArray(n) { hashes.getValue(items[it].cacheKey) }
        fun apart(a: Int, b: Int) = java.lang.Long.bitCount(hash[a] xor hash[b])

        // Photos are joined into groups pair by pair.
        val parent = IntArray(n) { it }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) {
                parent[r] = parent[parent[r]]
                r = parent[r]
            }
            return r
        }
        fun join(a: Int, b: Int) {
            parent[root(a)] = root(b)
        }

        // Bursts: neighbours in time. A few places ahead are looked at, in case another folder's photo falls in between.
        val byTime = (0 until n).filter { items[it].dateTaken > 0 }.sortedBy { items[it].dateTaken }
        for (k in byTime.indices) {
            val a = byTime[k]
            for (m in k + 1..minOf(k + 3, byTime.lastIndex)) {
                val b = byTime[m]
                if (items[b].dateTaken - items[a].dateTaken > BURST_MS) break
                val alike = items[a].bucketId == items[b].bucketId && items[a].width == items[b].width && items[a].height == items[b].height
                if (alike && apart(a, b) <= BURST_BITS) join(a, b)
            }
        }

        // Copies: any two photos of the same shape with near-identical fingerprints. A picture that is
        // mostly one flat colour has a fingerprint of nearly all zeros or ones, which would match any
        // other flat picture, so those are left out of this pass.
        val shape = FloatArray(n) { if (items[it].height > 0) items[it].width.toFloat() / items[it].height else 0f }
        val flat = BooleanArray(n) { java.lang.Long.bitCount(hash[it]) !in 8..56 }
        for (i in 0 until n) {
            if (flat[i]) continue
            for (j in i + 1 until n) {
                if (!flat[j] && apart(i, j) <= COPY_BITS && abs(shape[i] - shape[j]) < 0.03f) join(i, j)
            }
        }

        return (0 until n).groupBy { root(it) }.values
            .filter { it.size > 1 }
            .map { group -> group.map { items[it] }.sortedWith(compareByDescending<Photo> { it.width.toLong() * it.height }.thenByDescending { it.size }) }
            .sortedByDescending { group -> group.maxOf { it.time(PhotoSort.Taken) } }
    }
}
