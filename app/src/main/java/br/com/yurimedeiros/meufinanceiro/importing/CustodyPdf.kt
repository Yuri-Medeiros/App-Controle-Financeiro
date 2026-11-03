package br.com.yurimedeiros.meufinanceiro.importing

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

internal object CustodyPdf {
    fun read(bytes: ByteArray, cacheDir: File): CustodyStatement {
        require(bytes.size in 1..10 * 1024 * 1024)
        val file = File.createTempFile("custody-", ".pdf", cacheDir)
        try {
            file.writeBytes(bytes)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    require(pdf.pageCount in 1..30)
                    val text = StringBuilder()
                    repeat(pdf.pageCount) { index ->
                        pdf.openPage(index).use { page ->
                            page.textContents.forEach { text.append(it.text).append('\n') }
                        }
                        require(text.length <= 1_000_000)
                    }
                    CustodyStatements.parse(text.toString())
                }
            }
        } finally { file.delete() }
    }
}
