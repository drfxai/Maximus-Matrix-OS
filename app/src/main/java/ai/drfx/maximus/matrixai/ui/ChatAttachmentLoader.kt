package ai.drfx.maximus.matrixai.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.llm.ChatAttachment
import ai.drfx.maximus.matrixai.llm.ChatFileIntake
import ai.drfx.maximus.matrixai.llm.ChatFileIntake.Kind
import ai.drfx.maximus.matrixai.vision.ImageInputPipeline
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Reads a picked file into a chat attachment: images are normalised to JPEG, PDFs keep their
 * bytes plus extracted text, audio stays binary for audio-capable models, and text, HTML, code,
 * ZIP/RAR/7z/TAR archives and Office documents become plain text the model can read.
 */
internal fun loadChatAttachment(context: Context, uri: Uri): ChatAttachment {
    val resolver = context.contentResolver
    val reported = resolver.getType(uri) ?: "application/octet-stream"
    var name = context.getString(R.string.conv_attachment_default)
    var size = -1L
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    val head = resolver.openInputStream(uri)?.use { stream ->
        val buffer = ByteArray(1024)
        var read = 0
        while (read < buffer.size) {
            val count = stream.read(buffer, read, buffer.size - read)
            if (count < 0) break
            read += count
        }
        buffer.copyOf(read)
    } ?: throw IllegalStateException(context.getString(R.string.conv_attachment_read))
    require(head.isNotEmpty()) { context.getString(R.string.conv_attachment_empty) }
    val kind = ChatFileIntake.kindOf(name, reported, head)
    if (kind == Kind.IMAGE) {
        val prepared = ImageInputPipeline.prepare(context, uri, name)
        return ChatAttachment(name = prepared.name, mimeType = "image/jpeg",
            data = Base64.encodeToString(prepared.jpeg, Base64.NO_WRAP), isText = false,
            summary = "Image · " + ChatFileIntake.sizeLabel(prepared.jpeg.size.toLong()))
    }
    require(kind != Kind.UNSUPPORTED) {
        "“$name” is not a format Chat can read. Supported: text and code, HTML, PDF, Word/Excel/PowerPoint, ZIP/RAR/7z/TAR archives, images and audio."
    }
    val limit = when (kind) {
        Kind.AUDIO, Kind.PDF -> 10L * 1024 * 1024
        Kind.TEXT, Kind.HTML -> 5L * 1024 * 1024
        else -> 20L * 1024 * 1024
    }
    require(size < 0 || size <= limit) { "“$name” is " + ChatFileIntake.sizeLabel(size) + "; the limit for this type is " + ChatFileIntake.sizeLabel(limit) + "." }
    val bytes = resolver.openInputStream(uri)?.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "“$name” is larger than " + ChatFileIntake.sizeLabel(limit) + "." }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: throw IllegalStateException(context.getString(R.string.conv_attachment_read))
    require(bytes.isNotEmpty()) { context.getString(R.string.conv_attachment_empty) }
    return when (kind) {
        Kind.TEXT, Kind.HTML -> {
            require(ChatFileIntake.looksLikeText(bytes.copyOfRange(0, minOf(bytes.size, 64 * 1024)))) {
                context.getString(R.string.conv_text_binary)
            }
            val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
            val lines = text.count { it == '\n' } + 1
            val language = CodeLanguageCatalog.forFileName(name)
            ChatAttachment(name, if (kind == Kind.HTML) "text/html" else ChatFileIntake.textMime(name), text, true,
                summary = (language?.label ?: if (kind == Kind.HTML) "HTML" else "Text") + " · " + "%,d lines".format(lines))
        }
        Kind.PDF -> {
            val pages = pdfPages(context, bytes)
            ChatAttachment(name, "application/pdf", Base64.encodeToString(bytes, Base64.NO_WRAP), false,
                fallbackText = pages.second ?: ChatFileIntake.pdfText(bytes),
                summary = "PDF" + (pages.first?.let { " · $it pages" } ?: "") + " · " + ChatFileIntake.sizeLabel(bytes.size.toLong()))
        }
        Kind.AUDIO -> {
            val mime = ChatFileIntake.audioMime(name, reported)
            val duration = audioDurationMs(context, uri)
            ChatAttachment(name, mime, Base64.encodeToString(bytes, Base64.NO_WRAP), false, durationMs = duration,
                summary = "Audio · " + (if (duration > 0) "${duration / 1000}s · " else "") + ChatFileIntake.sizeLabel(bytes.size.toLong()))
        }
        else -> {
            val digest = when (kind) {
                Kind.ZIP -> ChatFileIntake.zip(name, bytes)
                Kind.RAR -> ChatFileIntake.rar(name, bytes)
                Kind.SEVEN_ZIP -> ChatFileIntake.sevenZip(name, bytes)
                Kind.TAR -> ChatFileIntake.tar(name, bytes, gzipped = head.size > 1 && head[0] == 0x1F.toByte())
                Kind.GZIP -> ChatFileIntake.gzip(name, bytes)
                else -> ChatFileIntake.office(name, bytes, kind)
            }
            ChatAttachment(name, "text/plain", digest.text, true, summary = digest.summary)
        }
    }
}

/** Page count and, on Android 15+, the platform's own text extraction. Never renders pages. */
private fun pdfPages(context: Context, bytes: ByteArray): Pair<Int?, String?> {
    val temp = File.createTempFile("chat_pdf_", ".pdf", context.cacheDir)
    return try {
        temp.writeBytes(bytes)
        android.os.ParcelFileDescriptor.open(temp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                val count = renderer.pageCount
                // getTextContents() exists only on newer platform builds; reflection keeps older
                // devices on the bundled extractor without a hard API dependency.
                val text = StringBuilder()
                runCatching {
                    for (index in 0 until minOf(count, 400)) {
                        renderer.openPage(index).use { page ->
                            val contents = page.javaClass.getMethod("getTextContents").invoke(page) as? List<*>
                            contents?.forEach { item ->
                                item?.javaClass?.getMethod("getText")?.invoke(item)?.toString()?.let { text.append(it).append('\n') }
                            }
                        }
                        text.append('\n')
                        if (text.length > ChatFileIntake.MAX_TOTAL_TEXT_CHARS) break
                    }
                }
                count to text.toString().trim().takeIf { it.length >= 20 }?.take(ChatFileIntake.MAX_TOTAL_TEXT_CHARS)
            }
        }
    } catch (_: Exception) {
        null to null
    } finally {
        temp.delete()
    }
}

private fun audioDurationMs(context: Context, uri: Uri): Long = runCatching {
    val retriever = android.media.MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } finally {
        retriever.release()
    }
}.getOrDefault(0L)
