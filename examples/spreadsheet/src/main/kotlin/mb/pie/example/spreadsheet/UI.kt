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
import mb.pie.vfs.path.PPath
import org.intellij.lang.annotations.JdkConstants
import java.awt.BorderLayout
import java.awt.Label
import java.util.*
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTabbedPane






class SpreadSheet(pie: PieImpl, root_task: TaskKey) : JFrame() {
    private val pie = pie
    private val root_task = root_task
    private val panel = JPanel()
    private val tabs = JTabbedPane()
    init {
        title = "HelloApp"

        panel.add(tabs, BorderLayout.CENTER);
        add(panel, BorderLayout.CENTER);
        isVisible = true
        refresh()
        pack();
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    }

    fun refresh() {
        tabs.removeAll()
        val sheets = tx().taskReqs(root_task);
        for (sheet in sheets) {
            val sheet_state = tx().data(sheet.callee) as TaskData<PPath,Int>;
            val sheet_name = sheet_state.input.javaPath.fileName
            val sheet_result = sheet_state.output
            val cells_state = all_dependents(tx(),sheet.callee).map { req -> tx().data(req) as TaskData<PPath,Int> }.toList()

            tabs.addTab(sheet_name.toString() + "($sheet_result)",SheetComponent(sheet_state,cells_state,{e -> update(e)} ))
        }
    }

    fun update(changed_file : PPath) {
        pie.bottomUpExecutor.requireBottomUp(setOf(changed_file))
        refresh()
    }

    fun tx() : StoreReadTxn {
        return pie.store.readTxn()
    }


    class SheetComponent(sheet_state: TaskData<PPath,Int>, cells_state: List<TaskData<PPath, Int>>,refresh:(PPath) -> Unit) : JPanel() {
        private val sheet_state =sheet_state
        val cells_state = cells_state
        val cells = JTabbedPane()
        init {
            setSize(300,300)
            add(Label("Result ${sheet_state.output}"))
            add(cells, BorderLayout.CENTER);
            for ( cell in cells_state) {
                val name = "${cell.input.javaPath.fileName.toString()} = ${cell.output}"
                cells.addTab(name,CellComponent(cell,refresh))
            }

        }
    }

    class CellComponent(state : TaskData<PPath,Int>,refresh:(PPath) -> Unit) : JPanel() {
        val content = JTextArea(10,5)
        private val state = state
        init {
            content.text = String(state.input.readAllBytes())
            add(content)
            val btn = JButton();
            btn.text = "Save"
            btn.addActionListener { e ->
                val out = state.input.outputStream();
                out.write(content.text.toByteArray())
                out.flush()
                refresh(state.input)
            }
            add(btn)
        }
    }
}




