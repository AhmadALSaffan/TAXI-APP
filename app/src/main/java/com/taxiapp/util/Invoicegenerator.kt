package com.taxiapp.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoiceGenerator {

    private const val PAGE_WIDTH  = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN      = 48f
    private const val LINE_HEIGHT = 24f

    fun generate(context: Context, trip: Trip): Result<String> {
        return try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page     = document.startPage(pageInfo)
            draw(page.canvas, trip)
            document.finishPage(page)

            val fileName = "invoice_${trip.tripId.take(8)}.pdf"
            val stream   = openOutputStream(context, fileName)
                ?: return Result.failure(Exception("Could not create file"))

            stream.use { document.writeTo(it) }
            document.close()
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun draw(canvas: Canvas, trip: Trip) {
        val navy   = Color.parseColor("#090E27")
        val blue   = Color.parseColor("#2233FF")
        val white  = Color.WHITE
        val gray   = Color.parseColor("#8A9BB0")
        val light  = Color.parseColor("#E8EDF5")
        val fmt    = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())

        val bgPaint = Paint().apply { color = navy; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

        val headerPaint = Paint().apply { color = blue; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 120f, headerPaint)

        val titlePaint = Paint().apply {
            color     = white
            textSize  = 28f
            isFakeBoldText = true
            isAntiAlias    = true
        }
        canvas.drawText("TAXI APP", MARGIN, 58f, titlePaint)

        val subPaint = Paint().apply {
            color    = Color.parseColor("#CCFFFFFF")
            textSize = 13f
            isAntiAlias = true
        }
        canvas.drawText("Trip Invoice", MARGIN, 80f, subPaint)

        val tripIdPaint = Paint().apply {
            color    = Color.parseColor("#CCFFFFFF")
            textSize = 11f
            isAntiAlias = true
        }
        val tripIdText = "Trip ID: ${trip.tripId.take(16).uppercase()}"
        val tripIdW    = tripIdPaint.measureText(tripIdText)
        canvas.drawText(tripIdText, PAGE_WIDTH - MARGIN - tripIdW, 58f, tripIdPaint)
        val dateText = if (trip.completedAt > 0) fmt.format(Date(trip.completedAt)) else fmt.format(Date(trip.createdAt))
        val dateW    = tripIdPaint.measureText(dateText)
        canvas.drawText(dateText, PAGE_WIDTH - MARGIN - dateW, 78f, tripIdPaint)

        var y = 148f

        val sectionPaint = Paint().apply {
            color    = blue
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias    = true
            letterSpacing  = 0.1f
        }

        val labelPaint = Paint().apply {
            color    = gray
            textSize = 12f
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color    = white
            textSize = 13f
            isAntiAlias = true
        }
        val boldValuePaint = Paint().apply {
            color    = white
            textSize = 13f
            isFakeBoldText = true
            isAntiAlias    = true
        }
        val dividerPaint = Paint().apply {
            color     = Color.parseColor("#1AFFFFFF")
            style     = Paint.Style.FILL
            strokeWidth = 1f
        }

        canvas.drawText("JOURNEY", MARGIN, y, sectionPaint)
        y += 18f

        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 1f, dividerPaint)
        y += 16f

        fun row(label: String, value: String, bold: Boolean = false) {
            canvas.drawText(label, MARGIN, y, labelPaint)
            val vp = if (bold) boldValuePaint else valuePaint
            canvas.drawText(value, MARGIN + 140f, y, vp)
            y += LINE_HEIGHT
        }

        row("From",    trip.pickupAddress.take(55))
        row("To",      trip.dropoffAddress.take(55))
        row("Status",  statusLabel(trip.status))
        if (trip.startedAt > 0)   row("Picked up",  SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(trip.startedAt)))
        if (trip.completedAt > 0) row("Dropped off", SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(trip.completedAt)))
        row("Distance", "${trip.distanceMiles} mi")
        row("Duration", "${trip.durationMinutes} min")

        y += 12f
        canvas.drawText("DRIVER", MARGIN, y, sectionPaint)
        y += 18f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 1f, dividerPaint)
        y += 16f

        if (trip.driverName.isNotBlank()) {
            row("Driver",  trip.driverName)
            row("Vehicle", "${trip.driverVehicle} · ${trip.driverPlate}")
            row("Rating",  "${trip.driverRating} ★")
        } else {
            canvas.drawText("No driver assigned", MARGIN, y, labelPaint)
            y += LINE_HEIGHT
        }

        y += 12f
        canvas.drawText("FARE BREAKDOWN", MARGIN, y, sectionPaint)
        y += 18f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 1f, dividerPaint)
        y += 16f

        fun fareRow(label: String, amount: Double) {
            canvas.drawText(label, MARGIN, y, labelPaint)
            val amtText = "$${"%.2f".format(amount)}"
            val amtW    = valuePaint.measureText(amtText)
            canvas.drawText(amtText, PAGE_WIDTH - MARGIN - amtW, y, valuePaint)
            y += LINE_HEIGHT
        }

        fareRow("Base Fare",     trip.baseFare)
        fareRow("Distance Fare", trip.distanceFare)
        fareRow("Time Fare",     trip.timeFare)
        if (trip.tolls > 0) fareRow("Tolls & Fees", trip.tolls)

        y += 4f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 1f, dividerPaint)
        y += 18f

        val totalLabel = "TOTAL"
        canvas.drawText(totalLabel, MARGIN, y, sectionPaint)
        val totalText  = "$${"%.2f".format(trip.price)}"
        val totalPaint = Paint().apply {
            color    = blue
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias    = true
        }
        val totalW = totalPaint.measureText(totalText)
        canvas.drawText(totalText, PAGE_WIDTH - MARGIN - totalW, y, totalPaint)
        y += 28f

        val paymentText = when {
            trip.paymentMethod.lowercase() == "cash" -> "CASH PAYMENT"
            trip.paymentMethod.length >= 4            -> "CARD ···· ${trip.paymentMethod.takeLast(4)}"
            else                                      -> "CARD PAYMENT"
        }
        val payW = labelPaint.measureText(paymentText)
        canvas.drawText(paymentText, PAGE_WIDTH - MARGIN - payW, y, labelPaint)

        y += 40f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 1f, dividerPaint)
        y += 20f

        val footerPaint = Paint().apply {
            color    = gray
            textSize = 10f
            isAntiAlias = true
        }
        canvas.drawText("Thank you for riding with TAXI APP.", MARGIN, y, footerPaint)
        y += 16f
        canvas.drawText("This is an automatically generated invoice.", MARGIN, y, footerPaint)
    }

    private fun statusLabel(status: String) = when (status) {
        TripStatus.COMPLETED -> "Completed"
        TripStatus.CANCELLED -> "Cancelled"
        else                 -> status.replaceFirstChar { it.uppercase() }
    }

    private fun openOutputStream(context: Context, fileName: String): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)
        } else {
            val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, fileName)
            FileOutputStream(file)
        }
    }
}