package com.truva.sandbox.patcher

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class AxmlEditorTest {

    @Test
    fun testAxmlModification() {
        val apkFile = File("build/outputs/apk/debug/Truva-debug.apk")
        if (!apkFile.exists()) {
            println("Truva-debug.apk not found, skipping axml test")
            return
        }

        val zipFile = ZipFile(apkFile)
        val entry = zipFile.getEntry("AndroidManifest.xml")
        val manifestBytes = zipFile.getInputStream(entry).use { it.readBytes() }
        zipFile.close()

        println("Original manifest size: ${manifestBytes.size}")

        val axmlEditor = AxmlEditor(manifestBytes)
        val withApp = axmlEditor.setApplicationName("com.truva.loader.ProxyApplication")
        val withoutSplit = AxmlEditor(withApp).removeSplitRequired()
        val finalManifest = AxmlEditor(withoutSplit).forceExtractNativeLibs()

        println("Final manifest size: ${finalManifest.size}")

        val outFile = File("build/AndroidManifest_patched.xml")
        outFile.writeBytes(finalManifest)
        println("Saved to ${outFile.absolutePath}")

        assertTrue("Manifest should have size > 0", finalManifest.size > 0)
    }
}
