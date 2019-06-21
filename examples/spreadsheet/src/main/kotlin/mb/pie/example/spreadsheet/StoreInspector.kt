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
import java.awt.event.KeyListener
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger


class StoreInspector() : JFrame() {
    private var idx : AtomicInteger = AtomicInteger(0)
    private val panel = JPanel()
    private val states = mutableListOf<GraphViz>()
    private val images = mutableListOf<BufferedImage>();
    init {
        title = "Store "
        isVisible = true
        add(panel, BorderLayout.CENTER);

        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        addMouseWheelListener { if (it.preciseWheelRotation > 0) { showImg(idx.get()+1)} else { showImg(idx.get()-1)}}

    }

    fun add_img(dump : BufferedImage) {
        images.add(dump)
        showImg(images.size-1);
    }

    fun showImg(i: Int) {
        idx.set((i + images.size) % images.size);
        panel.removeAll()
        panel.add(GraphViz(images[i]))
        title= "${idx.get()} of ${images.size-1}"
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
