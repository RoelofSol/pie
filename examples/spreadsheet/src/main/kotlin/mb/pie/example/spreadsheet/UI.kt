package mb.pie.example.spreadsheet

import mb.pie.api.Observable
import mb.pie.api.TaskKey
import mb.pie.runtime.PieImpl

import javax.swing.*
import mb.pie.api.StoreReadTxn
import mb.pie.api.TaskData
import mb.pie.vfs.path.PPath
import java.awt.BorderLayout
import java.awt.Label
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTabbedPane






class SpreadSheet(pie: PieImpl, root_task: TaskKey) : JFrame() {
    private val pie = pie
    private val root_task = root_task
    private val panel = JPanel()
    private var active : TaskKey? = null
    init {
        title = "HelloApp"



        val sheets = tx().taskReqs(root_task);

        for (sheet in sheets) {
            pie.store.writeTxn().setObservability(sheet.callee, false)
        }
        sheets.getOrNull(0)?.let { e -> setActiveSheet(e.callee)}

        isVisible = true
        refresh()
        pack();
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    }

    fun setActiveSheet(key : TaskKey) {
        if(key == active) { return }
        println("Activate $key : Disable : $active")
        active?.let { active ->  pie.store.writeTxn().setObservability(active ,false) }
        pie.store.writeTxn().setObservability(key,true);
        active = key
    }

    fun refresh() {
        panel.removeAll()
        val tabs = JTabbedPane()
        panel.add(tabs, BorderLayout.CENTER);


        val root = (tx().input(root_task) as MultiSheet.Input).workspace
        panel.add(PromptComponent(root,{e -> update(e)}),BorderLayout.SOUTH)
        add(panel, BorderLayout.CENTER);

        val sheets = tx().taskReqs(root_task);
        for (sheet in sheets) {
            val sheet_state = tx().data(sheet.callee) as TaskData<PPath,Int>;
            val sheet_name = sheet_state.input.javaPath.fileName
            val sheet_result = sheet_state.output
            val cells_state = all_dependents(tx(),sheet.callee).map { req -> tx().data(req) as TaskData<PPath,Int> }.toList()
            val component = SheetComponent(sheet.callee,sheet_state,cells_state,{e -> update(e)} )
            tabs.addTab(sheet_name.toString() + "($sheet_result)",component)
            if( sheet.callee == active ) {
                tabs.selectedComponent = component
            }
        }

        tabs.addChangeListener{ _ ->
            if (tabs.selectedComponent != null) {
                val selected = tabs.selectedComponent as SheetComponent
                setActiveSheet(selected.key)
            }
        }
    }

    fun update(changed_file : PPath) {
        println("Updated: ${changed_file}")
        pie.bottomUpExecutor.requireBottomUp(setOf(changed_file))
        println("Ok: ${changed_file}")
        refresh()
    }

    fun tx() : StoreReadTxn {
        return pie.store.readTxn()
    }


    class SheetComponent(key: TaskKey,sheet_state: TaskData<PPath,Int>, cells_state: List<TaskData<PPath, Int>>,refresh:(PPath) -> Unit) : JPanel() {
        val key = key
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
            btn.addActionListener { _ ->
                val out = state.input.outputStream();
                out.write(content.text.toByteArray())
                out.flush()
                refresh(state.input)
            }
            add(btn)
        }
    }


    class PromptComponent( root : PPath, refresh: (PPath) -> Unit) : JTextField(20) {
        init {
            addActionListener { _ ->
                val changed = root.resolve(this.text)
                refresh(changed)

             }
        }
    }
}




