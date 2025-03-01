package com.example.encryption

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.concurrent.Task
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.input.TransferMode
import javafx.scene.layout.GridPane
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import org.kordamp.ikonli.javafx.FontIcon
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ModernEncryptionController {

    @FXML private lateinit var fileTable: TableView<FileItem>
    @FXML private lateinit var chunkSizeCombo: ComboBox<String>
    @FXML private lateinit var progressBar: ProgressBar
    @FXML private lateinit var progressLabel: Label
    @FXML private lateinit var statusLabel: Label
    @FXML private lateinit var encryptButton: Button
    @FXML private lateinit var decryptButton: Button
    @FXML private lateinit var memoryLabel: Label
    @FXML private lateinit var itemCountLabel: Label
    @FXML private lateinit var themeMenu: Menu

    private lateinit var efs: EncryptedFileSystem
    private var currentDirectory: File? = null
    private lateinit var fileItems: ObservableList<FileItem>
    private var executorService: ScheduledExecutorService? = null
    private var currentTask: Task<Void>? = null

    @FXML
    fun initialize() {
        efs = EncryptedFileSystem()
        fileItems = FXCollections.observableArrayList()

        setupUI()
        setupTableColumns()
        setupChunkSizeCombo()
        setupDragAndDrop()
        setupMemoryMonitoring()
        setupThemes()
        fileTable.selectionModel.selectionMode = SelectionMode.MULTIPLE
        loadSettings()
    }

    private fun setupUI() {
        fileTable.items = fileItems
        encryptButton.graphic = FontIcon("fas-lock")
        decryptButton.graphic = FontIcon("fas-unlock")
        progressBar.progress = 0.0
        progressLabel.text = "준비"
        memoryLabel.text = "메모리: 초기화 중..."
        itemCountLabel.text = "항목 수: 0개"
    }

    private fun setupTableColumns() {
        val nameCol = TableColumn<FileItem, String>("이름").apply {
            setCellValueFactory { it.value.nameProperty() }
        }
        val typeCol = TableColumn<FileItem, String>("유형").apply {
            setCellValueFactory { it.value.typeProperty() }
        }
        val sizeCol = TableColumn<FileItem, String>("크기").apply {
            setCellValueFactory { it.value.sizeProperty() }
        }
        val statusCol = TableColumn<FileItem, String>("상태").apply {
            setCellValueFactory { it.value.statusProperty() }
        }
        fileTable.columns.addAll(nameCol, typeCol, sizeCol, statusCol)
    }

    private fun setupChunkSizeCombo() {
        chunkSizeCombo.items.addAll("1 MB", "16 MB", "32 MB", "64 MB", "128 MB", "256 MB", "512 MB", "1 GB")
        chunkSizeCombo.value = "32 MB"
    }

    private fun setupDragAndDrop() {
        fileTable.setOnDragOver { event ->
            if (event.dragboard.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY)
            }
            event.consume()
        }
        fileTable.setOnDragDropped { event ->
            val files = event.dragboard.files
            handleFileDrop(files)
            event.consume()
        }
    }

    private fun setupMemoryMonitoring() {
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService?.scheduleAtFixedRate({
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / (1024 * 1024)
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val freeMemory = runtime.freeMemory() / (1024 * 1024)
            val memoryInfo = "메모리: 사용 $usedMemory MB / 최대 $maxMemory MB / 여유 $freeMemory MB"
            Platform.runLater { memoryLabel.text = memoryInfo }
        }, 0, 5, TimeUnit.SECONDS)
    }

    private fun setupThemes() {
        val darkTheme = MenuItem("다크 테마").apply {
            setOnAction {
                fileTable.scene.stylesheets.setAll(javaClass.getResource("/styles/dark.css").toExternalForm())
            }
        }
        val lightTheme = MenuItem("라이트 테마").apply {
            setOnAction {
                fileTable.scene.stylesheets.setAll(javaClass.getResource("/styles/modern.css").toExternalForm())
            }
        }
        themeMenu.items.addAll(darkTheme, lightTheme)
    }

    fun shutdown() {
        executorService?.takeIf { !it.isShutdown }?.shutdown()
    }

    @FXML
    private fun onOpenFolder() {
        DirectoryChooser().apply {
            title = "폴더 선택"
        }.showDialog(null)?.let { directory ->
            currentDirectory = directory
            updateFileList()
        }
    }

    @FXML
    private fun onCreateKey() {
        val dialog = Dialog<String>().apply {
            title = "새 키 생성"
            headerText = "새 키를 위한 비밀번호 입력"
            dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
            dialogPane.content = GridPane().apply {
                hgap = 10.0
                vgap = 10.0
                val password = PasswordField()
                val confirm = PasswordField()
                add(Label("비밀번호:"), 0, 0)
                add(password, 1, 0)
                add(Label("확인:"), 0, 1)
                add(confirm, 1, 1)
                resultConverter = {
                    if (it == ButtonType.OK && password.text == confirm.text) password.text
                    else {
                        showAlert(Alert.AlertType.ERROR, "오류", "비밀번호가 일치하지 않습니다")
                        null
                    }
                }
            }
        }

        dialog.showAndWait().ifPresent { password ->
            try {
                FileChooser().apply {
                    title = "키 파일 저장"
                    initialFileName = "mykey.key"
                    initialDirectory = File(System.getProperty("user.home"))
                    extensionFilters.add(FileChooser.ExtensionFilter("Encryption Key (*.key)", "*.key"))
                }.showSaveDialog(fileTable.scene.window)?.let { keyFile ->
                    efs.generateKey(keyFile.path, password)
                    showAlert(Alert.AlertType.INFORMATION, "성공", "키가 성공적으로 생성되었습니다")
                    statusLabel.text = "키 로드됨: ${keyFile.path}"
                }
            } catch (e: Exception) {
                showAlert(Alert.AlertType.ERROR, "오류", e.message ?: "알 수 없는 오류")
            }
        }
    }

    @FXML
    private fun onLoadKey() {
        val dialog = Dialog<String>().apply {
            title = "키 로드"
            headerText = "키를 위한 비밀번호 입력"
            dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
            dialogPane.content = GridPane().apply {
                hgap = 10.0
                vgap = 10.0
                val password = PasswordField()
                add(Label("비밀번호:"), 0, 0)
                add(password, 1, 0)
                resultConverter = { if (it == ButtonType.OK) password.text else null }
            }
        }

        dialog.showAndWait().ifPresent { password ->
            try {
                FileChooser().apply {
                    title = "키 파일 선택"
                }.showOpenDialog(null)?.let { keyFile ->
                    efs.loadKey(keyFile.path, password)
                    showAlert(Alert.AlertType.INFORMATION, "성공", "키가 성공적으로 로드되었습니다")
                    statusLabel.text = "키 로드됨: ${keyFile.path}"
                }
            } catch (e: Exception) {
                showAlert(Alert.AlertType.ERROR, "오류", e.message ?: "알 수 없는 오류")
            }
        }
    }

    @FXML
    private fun onEncrypt() {
        val selectedItems = fileTable.selectionModel.selectedItems
        if (selectedItems.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "경고", "선택된 파일이 없습니다")
            return
        }

        Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "암호화 확인"
            headerText = "선택한 항목을 암호화하시겠습니까?"
        }.showAndWait().takeIf { it.orElse(null) == ButtonType.OK } ?: return

        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val futures = mutableListOf<Future<*>>()

        currentTask = object : Task<Void>() {
            override fun call(): Void? {
                val total = selectedItems.size
                if (total == 1 && !File(currentDirectory, selectedItems[0].name).isDirectory) {
                    val item = selectedItems[0]
                    val file = File(currentDirectory, item.name)
                    val tempDecrypted = File(currentDirectory, "temp_${item.name}")
                    futures.add(executor.submit {
                        try {
                            updateMessage("암호화 중: ${item.name}")
                            val chunkSize = parseChunkSize(chunkSizeCombo.value)
                            val encryptedPath = efs.encryptFile(file.path, chunkSize)
                            val decryptedPath = efs.decryptFile(encryptedPath, tempDecrypted.path)
                            val originalHash = calculateFileHash(file)
                            val decryptedHash = calculateFileHash(tempDecrypted)
                            if (originalHash == decryptedHash) {
                                file.delete()
                                tempDecrypted.delete()
                                Platform.runLater { item.status = "암호화됨" }
                            } else {
                                showAlert(Alert.AlertType.ERROR, "검증 실패", "${item.name}의 무결성 검증 실패")
                            }
                        } catch (e: AccessDeniedException) {
                            Platform.runLater { showAlert(Alert.AlertType.ERROR, "권한 오류", "${item.name}에 대한 접근 권한이 없습니다.") }
                        } catch (e: Exception) {
                            Platform.runLater { showAlert(Alert.AlertType.ERROR, "오류", e.message ?: "알 수 없는 오류") }
                        }
                    })
                } else {
                    val zipFile = File(currentDirectory, "encrypted_bundle.zip")
                    val tempDecryptedZip = File(currentDirectory, "temp_encrypted_bundle.zip")
                    try {
                        zipFiles(selectedItems, zipFile)
                    } catch (e: AccessDeniedException) {
                        Platform.runLater { showAlert(Alert.AlertType.ERROR, "권한 오류", "압축 중 접근 권한이 없는 파일이 있습니다.") }
                        return null
                    }
                    futures.add(executor.submit {
                        try {
                            updateProgress(0.5, 1.0)
                            updateMessage("암호화 중: ${zipFile.name}")
                            val chunkSize = parseChunkSize(chunkSizeCombo.value)
                            val encryptedPath = efs.encryptFile(zipFile.path, chunkSize)
                            val decryptedPath = efs.decryptFile(encryptedPath, tempDecryptedZip.path)
                            val originalHash = calculateFileHash(zipFile)
                            val decryptedHash = calculateFileHash(tempDecryptedZip)
                            if (originalHash == decryptedHash) {
                                zipFile.delete()
                                tempDecryptedZip.delete()
                                Platform.runLater {
                                    fileItems.clear()
                                    fileItems.add(FileItem(File(encryptedPath)))
                                    fileTable.refresh()
                                }
                            } else {
                                showAlert(Alert.AlertType.ERROR, "검증 실패", "압축 파일의 무결성 검증 실패")
                            }
                        } catch (e: AccessDeniedException) {
                            Platform.runLater { showAlert(Alert.AlertType.ERROR, "권한 오류", "${zipFile.name}에 대한 접근 권한이 없습니다.") }
                        } catch (e: Exception) {
                            Platform.runLater { showAlert(Alert.AlertType.ERROR, "오류", e.message ?: "알 수 없는 오류") }
                        }
                    })
                }

                futures.forEach { it.get() }
                executor.shutdown()
                executor.awaitTermination(1, TimeUnit.HOURS)

                updateProgress(1.0, 1.0)
                updateMessage("암호화 완료 (100%)")
                return null
            }
        }.apply {
            progressBar.progressProperty().bind(progressProperty())
            progressLabel.textProperty().bind(messageProperty())
            setOnSucceeded {
                progressBar.progressProperty().unbind()
                progressLabel.textProperty().unbind()
                progressBar.progress = 1.0
                progressLabel.text = "암호화 완료 (100%)"
                updateFileList()
            }
            setOnFailed {
                progressBar.progressProperty().unbind()
                progressLabel.textProperty().unbind()
                showAlert(Alert.AlertType.ERROR, "오류", exception?.message ?: "알 수 없는 오류")
            }
            Thread(this).start()
        }
    }

    @FXML
    private fun onDecrypt() {
        val encryptedFiles = fileTable.selectionModel.selectedItems.filter { it.name.endsWith(".lock") }
        if (encryptedFiles.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "경고", "선택된 암호화 파일이 없습니다")
            return
        }

        Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "복호화 확인"
            headerText = "선택한 파일을 복호화하시겠습니까?"
        }.showAndWait().takeIf { it.orElse(null) == ButtonType.OK } ?: return

        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val futures = mutableListOf<Future<*>>()

        currentTask = object : Task<Void>() {
            override fun call(): Void? {
                encryptedFiles.forEachIndexed { i, item ->
                    val file = File(currentDirectory, item.name)
                    futures.add(executor.submit {
                        try {
                            updateProgress(i.toDouble(), encryptedFiles.size.toDouble())
                            updateMessage("복호화 중: ${item.name}")
                            val decryptedPath = efs.decryptFile(file.path)
                            val decryptedFile = File(decryptedPath)
                            if (decryptedFile.name.endsWith(".zip")) {
                                unzipFile(decryptedFile, currentDirectory!!)
                            }
                            Platform.runLater {
                                item.status = "복호화 및 해제 완료"
                                fileTable.refresh()
                            }
                        } catch (e: AccessDeniedException) {
                            Platform.runLater { showAlert(Alert.AlertType.ERROR, "권한 오류", "${item.name}에 대한 접근 권한이 없습니다.") }
                        } catch (e: Exception) {
                            Platform.runLater { showAlert(Alert.AlertType.ERROR, "오류", e.message ?: "알 수 없는 오류") }
                        }
                    })
                }

                futures.forEach { it.get() }
                executor.shutdown()
                executor.awaitTermination(1, TimeUnit.HOURS)

                updateProgress(1.0, 1.0)
                updateMessage("복호화 완료 (100%)")
                return null
            }
        }.apply {
            progressBar.progressProperty().bind(progressProperty())
            progressLabel.textProperty().bind(messageProperty())
            setOnSucceeded {
                progressBar.progressProperty().unbind()
                progressLabel.textProperty().unbind()
                progressBar.progress = 1.0
                progressLabel.text = "복호화 완료 (100%)"
                updateFileList()
            }
            setOnFailed {
                progressBar.progressProperty().unbind()
                progressLabel.textProperty().unbind()
                showAlert(Alert.AlertType.ERROR, "오류", exception?.message ?: "알 수 없는 오류")
            }
            Thread(this).start()
        }
    }

    private fun zipFiles(items: ObservableList<FileItem>, zipFile: File) {
        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                items.forEach { item ->
                    val file = File(currentDirectory, item.name)
                    addToZip(file, zos, "")
                }
            }
        }
    }

    @FXML
    private fun onExit() {
        saveSettings()
        Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "종료 확인"
            headerText = "프로그램을 종료하시겠습니까?"
        }.showAndWait().takeIf { it.orElse(null) == ButtonType.OK }?.let {
            shutdown()
            Platform.exit()
        }
    }

    @FXML
    private fun cancelTask() {
        currentTask?.takeIf { it.isRunning }?.let {
            it.cancel()
            progressLabel.text = "작업 취소됨"
            progressBar.progress = 0.0
        }
    }

    private fun updateFileList() {
        fileItems.clear()
        currentDirectory?.takeIf { it.exists() }?.listFiles()?.forEach {
            fileItems.add(FileItem(it))
        }
        itemCountLabel.text = "항목 수: ${fileItems.size}개"
    }

    private fun handleFileDrop(files: List<File>) {
        files.forEach { fileItems.add(FileItem(it)) }
        updateFileList()
    }

    private fun parseChunkSize(sizeStr: String): Int {
        val (size, unit) = sizeStr.split(" ")
        var result = size.toInt()
        if (unit == "GB") result *= 1024
        return result * 1024 * 1024
    }

    private fun showAlert(type: Alert.AlertType, title: String, content: String) {
        Alert(type).apply {
            this.title = title
            contentText = content
            showAndWait()
        }
    }

    private fun addToZip(file: File, zos: ZipOutputStream, parentPath: String) {
        val zipEntryName = "$parentPath${file.name}${if (file.isDirectory) "/" else ""}"
        zos.putNextEntry(ZipEntry(zipEntryName))
        if (file.isDirectory) {
            zos.closeEntry()
            file.listFiles()?.forEach { addToZip(it, zos, zipEntryName) }
        } else {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(1024)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    zos.write(buffer, 0, len)
                }
            }
            zos.closeEntry()
        }
    }

    private fun unzipFile(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            val buffer = ByteArray(1024)
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val newFile = File(destDir, entry!!.name)
                if (entry!!.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
            }
        }
        zipFile.delete()
    }

    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return Base64.getEncoder().encodeToString(digest.digest())
    }

    private fun saveSettings() {
        val props = Properties().apply {
            setProperty("chunkSize", chunkSizeCombo.value)
            setProperty("lastDirectory", currentDirectory?.path ?: System.getProperty("user.home"))
        }
        try {
            FileOutputStream("settings.properties").use { fos ->
                props.store(fos, "PASSCODE Settings")
            }
        } catch (e: IOException) {
            showAlert(Alert.AlertType.ERROR, "설정 저장 실패", e.message ?: "알 수 없는 오류")
        }
    }

    private fun loadSettings() {
        val props = Properties()
        try {
            FileInputStream("settings.properties").use { fis ->
                props.load(fis)
            }
            chunkSizeCombo.value = props.getProperty("chunkSize", "32 MB")
            currentDirectory = File(props.getProperty("lastDirectory", System.getProperty("user.home")))
            updateFileList()
        } catch (e: IOException) {
            currentDirectory = File(System.getProperty("user.home"))
            updateFileList()
        }
    }

    @FXML
    private fun showInfo() {
        Dialog<Void>().apply {
            title = "PASSCODE 정보"
            headerText = "프로그램 정보"
            dialogPane.buttonTypes.add(ButtonType.OK)
            dialogPane.content = TextArea().apply {
                isEditable = false
                text = """
                    PASSCODE v${ModernEncryptionApp.version}
                    
                    사용법:
                    1. 폴더 또는 파일을 드래그 앤 드롭하거나 '폴더 열기'를 통해 선택하세요.
                    2. '새 키 생성' 또는 '키 로드'를 통해 암호화 키를 설정하세요.
                    3. '암호화' 버튼으로 파일/폴더를 암호화하거나, '복호화' 버튼으로 복원하세요.
                    
                    사용된 라이브러리:
                    - JavaFX: UI 구현
                    - Ikonli: 아이콘 제공
                    - Java Cryptography Architecture (JCA): 암호화/복호화
                """.trimIndent()
            }
        }.showAndWait()
    }
}