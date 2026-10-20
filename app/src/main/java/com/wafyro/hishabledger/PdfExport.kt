package com.wafyro.hishabledger

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.*

internal fun buildShopPdf(vm: HishabViewModel, shop: Shop): ByteArray {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val left = 36f
    val right = 559f
    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create())
    var canvas = page.canvas
    var y = 0f
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(15, 89, 66); textSize = 22f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 11f }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 10f }
    val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 10f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1f }
    val history = vm.activeTxs(shop.id).sortedBy { it.transactionDate }

    fun header() {
        canvas.drawText("HishabLedger", left, 38f, titlePaint)
        titlePaint.textSize = 15f
        canvas.drawText(shop.name.take(42), left, 62f, titlePaint)
        titlePaint.textSize = 22f
        canvas.drawText("Account Statement", left, 84f, labelPaint)
        canvas.drawText("Generated: ${formatFull(System.currentTimeMillis())}", left, 101f, labelPaint)
        canvas.drawText("Current balance: ${money(vm.balance(shop.id))}", left, 124f, boldPaint)
        canvas.drawText("Total added: ${money(vm.totalDueAdded(shop.id))}", left, 143f, labelPaint)
        canvas.drawText("Total paid: ${money(vm.totalPaid(shop.id))}", left + 245f, 143f, labelPaint)
        canvas.drawLine(left, 156f, right, 156f, linePaint)
        canvas.drawText("Type / Note", 160f, 174f, boldPaint)
        canvas.drawText("Amount", 420f, 174f, boldPaint)
        canvas.drawText("Balance", 500f, 174f, boldPaint)
        canvas.drawLine(left, 181f, right, 181f, linePaint)
        y = 198f
    }
    fun startPage() {
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
        page = document.startPage(info)
        canvas = page.canvas
        header()
    }
    fun finishPage() {
        canvas.drawLine(left, 812f, right, 812f, linePaint)
        canvas.drawText("Wafyro Software  •  Page ${pageNumber - 1}", left, 829f, labelPaint)
        document.finishPage(page)
    }
    header()
    var runningBalance = 0L
    history.forEach { t ->
        runningBalance += if (t.type == TxType.PAYMENT) -t.amount else t.amount
        val note = t.note.replace('\n', ' ').take(34)
        val type = when (t.type) { TxType.PAYMENT -> "Payment"; TxType.OPENING_BALANCE -> "Opening balance"; TxType.DUE -> "Due" }
        if (y > 785f) { finishPage(); startPage() }
        canvas.drawText(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(t.transactionDate)), left, y, bodyPaint)
        canvas.drawText((type + if (note.isNotBlank()) " • $note" else "").take(40), 160f, y, bodyPaint)
        canvas.drawText(money(t.amount).take(15), 420f, y, bodyPaint)
        canvas.drawText(money(runningBalance).take(15), 500f, y, bodyPaint)
        y += 19f
        canvas.drawLine(left, y - 7f, right, y - 7f, linePaint)
    }
    if (history.isEmpty()) canvas.drawText("No transactions in this account yet.", left, y, bodyPaint)
    finishPage()
    val output = java.io.ByteArrayOutputStream()
    document.writeTo(output)
    document.close()
    return output.toByteArray()
}
