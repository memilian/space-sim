package nebulae

import com.kotcrab.vis.ui.widget.file.FileUtils
import java.io.File
import java.lang.IllegalStateException
import java.nio.file.*
import java.util.concurrent.CopyOnWriteArrayList


class ShaderWatcher() : Runnable {

    companion object {
        val changedPaths = CopyOnWriteArrayList<String>()
    }

    override fun run() {
        val inProp = System.getProperty("shaders.input.dir");
        val outProp = System.getProperty("shaders.output.dir");
        if (inProp == null || inProp.isEmpty()) throw IllegalStateException("System property shaders.input.dir not set")
        if (outProp == null || outProp.isEmpty()) throw IllegalStateException("System property shaders.input.dir not set")
        val inPath: Path = Path.of(inProp)
        val outPath: Path = Path.of(outProp)
        println("Watching for changes in $inPath")
        FileSystems.getDefault().newWatchService().use { watchService ->
            val keys = mutableMapOf<WatchKey, String>()
            keys[inPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)] = ""
            val baseDir = inPath.toFile()
            val listFiles = baseDir.listFiles()
            listFiles?.forEach { child ->
                if (child.isDirectory) {
                    keys[Path.of(child.absolutePath).register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)] = child.name;
                }
            }
            while (true) {
                val wk = watchService.take()
                if (wk != null) {
                    for (event in wk.pollEvents()) { //we only register "ENTRY_MODIFY" so the context is always a Path.
                        val changed: Path = event.context() as Path
                        if (changed.toString().endsWith("glsl")) {
                            println("File has changed : $changed")
                            try {
                                Files.copy(Path.of(inPath.toString(), keys[wk].toString(), changed.toString()), Path.of(outPath.toString(), keys[wk].toString(), changed.toString()), StandardCopyOption.REPLACE_EXISTING)
                            } catch (e: Exception) {
                                println(e)
                            }
                            changedPaths.add(changed.toString())
                        }
                    }
                    // reset the key
                    wk.reset()
                }
            }
        }
    }
}