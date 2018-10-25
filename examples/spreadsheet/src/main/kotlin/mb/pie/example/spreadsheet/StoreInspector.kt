package mb.pie.example.spreadsheet

import mb.pie.api.*
import mb.pie.runtime.PieImpl

import javax.swing.*
import mb.pie.runtime.store.InMemoryStore
import mb.pie.runtime.store.StoreDump
import mb.pie.vfs.path.PPath
import java.awt.BorderLayout
import java.awt.Label
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTabbedPane
import java.awt.Graphics
import java.awt.Panel
import java.io.IOException
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.TimeUnit
import java.awt.Dimension
import java.awt.Color
import javax.swing.BorderFactory




class StoreInspector() : JFrame() {
    private var idx = 0
    private val panel = JPanel()
    private val states = mutableListOf<GraphViz>()

    init {
        title = "Store"
        isVisible = true
        add(panel, BorderLayout.CENTER);

       // panel.add(MyPanel())
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    }

    fun add_state(dump : StoreDump) {
        panel.removeAll()
        panel.add(GraphViz(build_image(dump)))
        pack()
    }

    inner class GraphViz(image : BufferedImage) : JPanel() {

        private val image = image

        override fun getPreferredSize(): Dimension {
            return Dimension(image.width,image.height)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.drawImage(image, 0, 0, null) // see javadoc for more info on the parameters
        }


    }
}



fun build_image(dump: StoreDump) : BufferedImage {
    try {
        val proc = ProcessBuilder(listOf("dot","-Tjpeg"))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        proc.outputStream.write(toGraph(dump).toByteArray())
        proc.outputStream.flush()
        proc.outputStream.close()
        val buffer = proc.inputStream.buffered()
        proc.waitFor();
        return ImageIO.read(buffer)
    } catch (ex: IOException) {
        throw ex
    }
}


fun toGraph(dump : StoreDump) : String {

    val keys = hashSetOf<TaskKey>()
    val get_id = { key : TaskKey -> keys.add(key); key.hashCode()}
    val files = hashSetOf<PPath>()
    val get_file = { key : PPath -> files.add(key); key.hashCode()}

    val taskReqs = dump.taskReqs.flatMap{ (e,v) -> v.map {  caller -> "${get_id(e)} -> ${get_id(caller.callee)} [arrowhead=dot]" } }
    val fileReqs = dump.fileReqs.flatMap { (e,v) -> v.map{ filereq -> "${get_id(e)} -> ${get_file(filereq.file)} [arrowhead=normal]"} }

    val key_labels = keys.map{ k ->
        val color = when (dump.observables.get(k)) {
            Observability.Attached -> "#40e0d0"
            Observability.ForcedDetached -> "#000000"
            Observability.Detached -> "#333333"
            null -> TODO()
        }
        """${k.hashCode()} [label="${k.id}",color="${color}"]"""}
    val file_labels = files.map{ k ->
        val label = "${k.javaPath.parent.fileName}/${k.javaPath.fileName}"
        """${k.hashCode()} [label="${label}"]"""}

    val result = """
                digraph G {
                    node [shape=box]
                    ${key_labels.joinToString("\n")}
                    ${file_labels.joinToString("\n")}
                    ${taskReqs.joinToString("\n")}
                    ${fileReqs.joinToString("\n")}
                }
        """
    return result
}


