package mb.pie.example.spreadsheet

import mb.pie.api.*
import mb.pie.api.fs.FileSystemResource
import mb.pie.runtime.store.StoreDump
import java.awt.BorderLayout
import javax.swing.JFrame
import javax.swing.JPanel
import java.awt.Graphics
import java.io.IOException
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Paths


class StoreInspector() : JFrame() {
    private var idx = 0
    private val panel = JPanel()
    private val states = mutableListOf<GraphViz>()

    init {
        title = "Store"
        isVisible = true
        add(panel, BorderLayout.CENTER);

        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    }

    fun add_img(dump : BufferedImage) {
        panel.removeAll()
        panel.add(GraphViz(dump))
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
