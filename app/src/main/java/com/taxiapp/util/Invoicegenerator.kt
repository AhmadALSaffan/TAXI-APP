package com.taxiapp.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.res.ResourcesCompat
import com.taxiapp.R
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds a one-page PDF receipt in the Signal Amber style and saves it to Downloads. */
object InvoiceGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    private val graphite = Color.parseColor("#0E0F11")
    private val amber = Color.parseColor("#FFB224")
    private val amberDeep = Color.parseColor("#9A6400")
    private val ink = Color.parseColor("#111214")
    private val muted = Color.parseColor("#64676C")
    private val line = Color.parseColor("#E3E3DF")
    private val paper = Color.parseColor("#FFFFFF")

    /** Returns the saved file's content URI so it can be opened straight away. */
    fun generate(context: Context, trip: Trip): Result<Uri> = runCatching {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val font = runCatching { ResourcesCompat.getFont(context, R.font.manrope) }.getOrNull() ?: Typeface.DEFAULT
        draw(page.canvas, trip, font)
        document.finishPage(page)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "TAXI-APP-receipt-${trip.tripId.takeLast(8)}.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create file")
        try {
            resolver.openOutputStream(uri)?.use { document.writeTo(it) } ?: error("Could not write file")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        } finally {
            document.close()
        }
        uri
    }

    private fun draw(canvas: Canvas, trip: Trip, font: Typeface) {
        val regular = Typeface.create(font, 500, false)
        val bold = Typeface.create(font, 800, false)
        fun paint(color: Int, size: Float, face: Typeface = regular) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; typeface = face
        }
        val right = PAGE_WIDTH - MARGIN
        fun drawRight(text: String, y: Float, p: Paint) = canvas.drawText(text, right - p.measureText(text), y, p)

        canvas.drawColor(paper)

        // Header band
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 150f, Paint().apply { color = graphite })
        canvas.drawRect(0f, 150f, PAGE_WIDTH.toFloat(), 156f, Paint().apply { color = amber })
        canvas.drawRoundRect(RectF(MARGIN, 48f, MARGIN + 34f, 82f), 9f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = amber })
        canvas.drawCircle(MARGIN + 17f, 65f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = graphite })
        canvas.drawText("TAXI APP", MARGIN + 48f, 73f, paint(Color.WHITE, 22f, bold))
        canvas.drawText("Trip receipt", MARGIN, 116f, paint(Color.parseColor("#8F9297"), 13f))

        val when_ = if (trip.completedAt > 0) trip.completedAt else trip.createdAt
        drawRight(SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(when_)), 73f, paint(Color.WHITE, 12f))
        drawRight("Trip ${trip.tripId.takeLast(10).uppercase()}", 92f, paint(Color.parseColor("#8F9297"), 11f))

        // Total
        var y = 214f
        canvas.drawText("Total", MARGIN, y, paint(muted, 12f))
        y += 46f
        canvas.drawText(money(trip.price), MARGIN, y, paint(ink, 44f, bold))
        y += 24f
        canvas.drawText(paymentText(trip.paymentMethod) + "  ·  " + statusText(trip.status), MARGIN, y, paint(muted, 12f))

        // Route
        y += 46f
        y = section(canvas, "JOURNEY", y, paint(amberDeep, 10f, bold))
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = amber }
        val sq = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        val time = SimpleDateFormat("h:mm a", Locale.getDefault())
        canvas.drawCircle(MARGIN + 5f, y - 4f, 5f, dot)
        canvas.drawRect(MARGIN + 4f, y + 4f, MARGIN + 6f, y + 26f, Paint().apply { color = line })
        canvas.drawText(trip.pickupAddress.take(64), MARGIN + 22f, y, paint(ink, 13f))
        if (trip.startedAt > 0) drawRight(time.format(Date(trip.startedAt)), y, paint(muted, 11f))
        y += 34f
        canvas.drawRect(MARGIN, y - 9f, MARGIN + 10f, y + 1f, sq)
        canvas.drawText(trip.dropoffAddress.take(64), MARGIN + 22f, y, paint(ink, 13f, bold))
        if (trip.completedAt > 0) drawRight(time.format(Date(trip.completedAt)), y, paint(muted, 11f))
        y += 22f
        canvas.drawText(
            String.format(Locale.US, "%s km  ·  %d min  ·  %s", formatKm(trip.distanceMiles), trip.durationMinutes, trip.rideType.replaceFirstChar { it.uppercase() }),
            MARGIN + 22f, y, paint(muted, 11f),
        )

        // Driver
        if (trip.driverName.isNotBlank()) {
            y += 40f
            y = section(canvas, "DRIVER", y, paint(amberDeep, 10f, bold))
            canvas.drawText(trip.driverName, MARGIN, y, paint(ink, 13f, bold))
            drawRight(listOf(trip.driverVehicle, trip.driverPlate).filter { it.isNotBlank() }.joinToString("  ·  "), y, paint(muted, 12f))
        }

        // Fare
        y += 40f
        y = section(canvas, "FARE", y, paint(amberDeep, 10f, bold))
        val label = paint(muted, 12f)
        val value = paint(ink, 12f)
        fun row(name: String, amount: Double) {
            canvas.drawText(name, MARGIN, y, label)
            drawRight(money(amount), y, value)
            y += 24f
        }
        row("Base fare", trip.baseFare)
        row("Distance (${formatKm(trip.distanceMiles)} km)", trip.distanceFare)
        row("Time (${trip.durationMinutes} min)", trip.timeFare)
        if (trip.tolls > 0) row("Tolls & fees", trip.tolls)
        val subtotal = trip.baseFare + trip.distanceFare + trip.timeFare + trip.tolls
        if (trip.price - subtotal > 0.009) row("Minimum fare adjustment", trip.price - subtotal)
        canvas.drawRect(MARGIN, y - 10f, right, y - 9f, Paint().apply { color = line })
        y += 14f
        canvas.drawText("Total", MARGIN, y, paint(ink, 14f, bold))
        drawRight(money(trip.price), y, paint(ink, 14f, bold))

        // Footer
        val foot = paint(muted, 10f)
        canvas.drawRect(MARGIN, PAGE_HEIGHT - 86f, right, PAGE_HEIGHT - 85f, Paint().apply { color = line })
        canvas.drawText("Thanks for riding with TAXI APP.", MARGIN, PAGE_HEIGHT - 62f, foot)
        canvas.drawText("This receipt was generated automatically.", MARGIN, PAGE_HEIGHT - 46f, foot)
    }

    private fun section(canvas: Canvas, title: String, y: Float, p: Paint): Float {
        canvas.drawText(title, MARGIN, y, p.apply { letterSpacing = 0.12f })
        canvas.drawRect(MARGIN, y + 10f, PAGE_WIDTH - MARGIN, y + 11f, Paint().apply { color = line })
        return y + 36f
    }

    private fun money(v: Double) = formatMoney(v)

    private fun paymentText(id: String) = when (id.lowercase()) {
        "cash" -> "Paid in cash"
        "wallet" -> "Paid from wallet"
        else -> if (id.length >= 4) "Card ending ${id.takeLast(4)}" else "Card"
    }

    private fun statusText(status: String) = when (status) {
        TripStatus.COMPLETED -> "Completed"
        TripStatus.CANCELLED -> "Cancelled"
        else -> status.replaceFirstChar { it.uppercase() }
    }
}
