package mb.pie.runtime

import mb.pie.api.Observability
import mb.pie.api.Pie
import mb.pie.api.ResourceRequireDep
import mb.pie.api.TaskKey
import mb.pie.runtime.store.InMemoryStore
import mb.pie.runtime.store.StoreDump
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO


fun build_image(pie: Pie, outputFile : String = "img.jpeg")  {
    try {
        val dump = ((pie as PieImpl).store as InMemoryStore).dump();

        val proc = ProcessBuilder(listOf("bash","-c","tee \"${outputFile}.dot\" | dot -Tjpeg | tee \"${outputFile}.jpeg\""))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        val graph = toGraph(dump).toByteArray();
        proc.outputStream.buffered().use {
            it.write(graph)
        }
        return proc.inputStream.buffered().use {
            ImageIO.read(it)
    }
    } catch (ex: IOException) {
        throw ex
    }
}


fun toGraph(dump : StoreDump) : String {

    val keys = hashSetOf<TaskKey>()
    val get_id = { key : TaskKey -> keys.add(key); key.hashCode()}
    val files = hashSetOf<Path>()
    val get_file = { key : Path -> files.add(key.toAbsolutePath()); key.toAbsolutePath().hashCode()}

    val taskReqs = dump.taskReqs.flatMap{ (e,v) -> v.map {  caller -> "${get_id(e)} -> ${get_id(caller.callee)} [arrowhead=dot]" } }
    val fileReqs = dump.fileReqs.flatMap { (e,v) -> v.map{ filereq -> "${get_id(e)} -> ${get_file(Paths.get(filereq.key.key.toString()))} [arrowhead=normal]"} }


   /* for ( (e,v) in dump.fileReqs) {
        var set = mutableSetOf<ResourceRequireDep>();
        for ( p in v ) {
            if ( set.add( p ) ) {
                println( e );
                println( v )
                println( p )
                throw  Error("duplicate ");
            }
        }
    }*/

    val key_labels = keys.map{ k ->
        val color = when (dump.observables.getOrDefault(k, Observability.Attached)) {
            Observability.Attached -> "#40e0d0"
            Observability.Observed -> "#ff00ff"
            Observability.Detached -> "#333333"
        }
        """${k.hashCode()} [label="${k.id}\n${k.key.javaClass.name}",color="${color}"]"""}
    val file_labels = files.map{ k ->
        val label = "${ k.parent.fileName }/${k.fileName } (${k.hashCode()})"
        """${k.hashCode()} [label="${label}"]"""}

    val result = """
                digraph G {
                    rankdir=LR
                    node [shape=box]
                    ${key_labels.joinToString("\n")}
                    subgraph tmp {
                    rank=same
                    ${file_labels.joinToString("\n")}
                    }
                    ${taskReqs.joinToString("\n")}
                    ${fileReqs.joinToString("\n")}
                }
        """
    return result
}
