package br.com.cliplocal

import android.app.Activity
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Salva vídeos públicos em Downloads/ClipLocal usando yt-dlp, FFmpeg
 * e MediaStore, sem permissões amplas de armazenamento.
 */
class MainActivity : Activity() {
    private lateinit var linkInput: EditText
    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var pasteButton: Button
    private lateinit var cancelButton: Button
    private lateinit var openButton: Button
    private lateinit var updateButton: Button

    private val worker = Executors.newSingleThreadExecutor()
    private var busy = false
    @Volatile private var cancelRequested = false
    private var savedVideo: Uri? = null
    private var savedMime = "video/mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        linkInput = findViewById(R.id.linkInput)
        statusText = findViewById(R.id.statusText)
        progressText = findViewById(R.id.progressText)
        progressBar = findViewById(R.id.progressBar)
        downloadButton = findViewById(R.id.downloadButton)
        pasteButton = findViewById(R.id.pasteButton)
        cancelButton = findViewById(R.id.cancelButton)
        openButton = findViewById(R.id.openButton)
        updateButton = findViewById(R.id.updateButton)

        pasteButton.setOnClickListener { pasteFromClipboard() }
        downloadButton.setOnClickListener { startDownload() }
        cancelButton.setOnClickListener { cancelDownload() }
        openButton.setOnClickListener { openLastVideo() }
        updateButton.setOnClickListener { updateExtractor() }
        readSharedText(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSharedText(intent)
    }

    private fun readSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        linkInput.setText(XUrlParser.extract(text) ?: text)
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            showToast("Não há texto na área de transferência.")
            return
        }
        linkInput.setText(XUrlParser.extract(text) ?: text)
    }

    private fun startDownload() {
        if (busy) return
        val url = XUrlParser.extract(linkInput.text.toString())
        if (url == null) {
            linkInput.error = "Cole o link de um post do x.com ou twitter.com"
            return
        }

        cancelRequested = false
        setBusy(true, canCancel = true)
        progressBar.progress = 0
        progressText.text = "0%"
        statusText.text = "Preparando o download..."

        worker.execute {
            val workingDirectory = File(cacheDir, "clip_${System.nanoTime()}")
            try {
                if (!workingDirectory.mkdirs()) error("Não foi possível criar pasta temporária")

                runOnUiThread { statusText.text = "Iniciando mecanismo de vídeo..." }
                YoutubeDL.init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                checkCancellation()

                val request = YoutubeDLRequest(url).apply {
                    // Prefere vídeo e áudio MP4, com alternativas disponíveis.
                    addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/best")
                    addOption("--merge-output-format", "mp4")
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("--no-part")
                    addOption("--newline")
                    addOption("--retries", 3)
                    addOption("-o", File(workingDirectory, "video.%(ext)s").absolutePath)
                }

                runOnUiThread { statusText.text = "Baixando vídeo..." }
                YoutubeDL.execute(request, PROCESS_ID, false) { percentage, _, _ ->
                    if (!cancelRequested) {
                        runOnUiThread {
                            if (busy && !cancelRequested) {
                                val progress = percentage.roundToInt().coerceIn(0, 100)
                                progressBar.progress = progress
                                progressText.text = "$progress%"
                            }
                        }
                    }
                }
                checkCancellation()

                val mediaFile = workingDirectory.listFiles()
                    ?.filter { it.isFile && it.length() > 0L &&
                        it.extension.lowercase(Locale.ROOT) in setOf("mp4", "mkv", "webm", "mov") }
                    ?.maxByOrNull { it.length() }
                    ?: error("O vídeo não foi encontrado após o download")

                runOnUiThread { statusText.text = "Salvando em Downloads/ClipLocal..." }
                val (uri, mime) = saveInDownloads(mediaFile, XUrlParser.postId(url))
                if (cancelRequested) {
                    contentResolver.delete(uri, null, null)
                    throw CancellationException("Cancelado pelo usuário")
                }

                runOnUiThread {
                    savedVideo = uri
                    savedMime = mime
                    progressBar.progress = 100
                    progressText.text = "100%"
                    statusText.text = "Vídeo salvo em Downloads/ClipLocal!"
                    openButton.visibility = View.VISIBLE
                    showToast("Download concluído!")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Falha ao baixar", error)
                runOnUiThread {
                    statusText.text = if (cancelRequested || error is CancellationException) {
                        "Download cancelado."
                    } else {
                        friendlyError(error)
                    }
                }
            } finally {
                workingDirectory.deleteRecursively()
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun cancelDownload() {
        if (!busy) return
        cancelRequested = true
        cancelButton.isEnabled = false
        statusText.text = "Cancelando..."
        YoutubeDL.destroyProcessById(PROCESS_ID)
    }

    private fun checkCancellation() {
        if (cancelRequested) throw CancellationException("Cancelado pelo usuário")
    }

    private fun saveInDownloads(file: File, postId: String): Pair<Uri, String> {
        val extension = file.extension.lowercase(Locale.ROOT)
        val mime = when (extension) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            else -> "video/mp4"
        }
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "X_${postId}_$date.$extension"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ClipLocal")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o arquivo em Downloads")

        try {
            val output = contentResolver.openOutputStream(uri)
                ?: error("Não foi possível abrir o arquivo de destino")
            output.use { destination ->
                FileInputStream(file).use { source ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancellation()
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
            }
            checkCancellation()
            contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
            return uri to mime
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun updateExtractor() {
        if (busy) return
        setBusy(true)
        statusText.text = "Atualizando o yt-dlp..."
        worker.execute {
            try {
                YoutubeDL.init(applicationContext)
                val result = YoutubeDL.updateYoutubeDL(
                    applicationContext,
                    YoutubeDL.UpdateChannel.STABLE
                )
                runOnUiThread {
                    statusText.text = when (result) {
                        YoutubeDL.UpdateStatus.DONE -> "Mecanismo atualizado com sucesso!"
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Mecanismo já está atualizado."
                        else -> "Atualização finalizada."
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Falha na atualização", error)
                runOnUiThread {
                    statusText.text = "Falha ao atualizar: ${error.message.orEmpty().take(140)}"
                }
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun openLastVideo() {
        val uri = savedVideo ?: return
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, savedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(viewIntent, "Abrir vídeo com"))
        } catch (_: Exception) {
            showToast("Abra o vídeo pelo app Arquivos > Downloads > ClipLocal")
        }
    }

    private fun setBusy(value: Boolean, canCancel: Boolean = false) {
        busy = value
        downloadButton.isEnabled = !value
        pasteButton.isEnabled = !value
        updateButton.isEnabled = !value
        cancelButton.visibility = if (value && canCancel) View.VISIBLE else View.GONE
        cancelButton.isEnabled = value && canCancel
    }

    private fun friendlyError(error: Exception): String {
        val msg = error.message.orEmpty()
        return when {
            msg.contains("authenticate", true) || msg.contains("login", true) ||
                msg.contains("cookies", true) -> "O X pediu autenticação para esse post. Tente outro vídeo público."
            msg.contains("403", true) -> "O X bloqueou o acesso a este vídeo (erro 403). Tente atualizar o mecanismo."
            msg.contains("no video", true) || msg.contains("not available", true) ->
                "Nenhum vídeo público disponível nesse post."
            else -> "Não foi possível baixar: ${msg.ifBlank { "erro desconhecido" }.take(150)}"
        }
    }

    private fun showToast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        cancelRequested = true
        YoutubeDL.destroyProcessById(PROCESS_ID)
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClipLocal"
        private const val PROCESS_ID = "cliplocal-download"
    }
}
