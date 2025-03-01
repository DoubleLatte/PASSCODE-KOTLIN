package com.example.encryption

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage

class ModernEncryptionApp : Application() {
    companion object {
        private const val VERSION = "1.0.0-alpha"
        private const val FXML_PATH = "/views/MainView.fxml"
        private const val CSS_PATH = "/styles/modern.css"

        val version: String // 속성으로 대체
            get() = VERSION

        @JvmStatic
        fun main(args: Array<String>) {
            launch(ModernEncryptionApp::class.java, *args)
        }
    }

    private lateinit var controller: ModernEncryptionController

    override fun start(primaryStage: Stage) {
        val loader = FXMLLoader(javaClass.getResource(FXML_PATH))
        val root: Parent = loader.load()
        controller = loader.getController()

        primaryStage.apply {
            title = "PASSCODE v$VERSION"
            scene = Scene(root).apply {
                stylesheets.add(javaClass.getResource(CSS_PATH).toExternalForm())
            }
            show()
        }
    }

    override fun stop() {
        if (::controller.isInitialized) {
            controller.shutdown()
        }
    }
}