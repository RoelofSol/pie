package mb.pie.example.spreadsheet

import mb.pie.api.TaskKey
import mb.pie.runtime.PieImpl

import javax.swing.*
import javax.swing.SwingUtilities
import javax.swing.JOptionPane
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import com.sun.java.accessibility.util.AWTEventMonitor.addActionListener
import mb.pie.api.Store
import mb.pie.api.StoreReadTxn
import mb.pie.api.TaskData
import mb.pie.runtime.store.InMemoryStore
import mb.pie.vfs.path.PPath
import org.intellij.lang.annotations.JdkConstants
import java.awt.BorderLayout
import java.awt.Label
import java.util.*
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTabbedPane






class StateExplorer(pie: PieImpl) : JFrame() {
    private val pie = pie
    private val panel = JPanel()


    init {
        title = "State"
        add(panel, BorderLayout.CENTER);
        panel.add(JLabel(tx().dump().toString()))
        isVisible = true
        pack();
        setSize(300,300)
        this.setLocation(0,400)
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        val tmp = pie.store
        tmp.readTxn()

    }


    fun tx() : InMemoryStore {
        return pie.store.readTxn() as InMemoryStore
    }

}



