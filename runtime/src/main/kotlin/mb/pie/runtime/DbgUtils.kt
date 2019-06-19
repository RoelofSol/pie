package mb.pie.runtime

import mb.pie.api.Observability
import mb.pie.api.TaskKey
import mb.pie.runtime.store.InMemoryStore
import mb.pie.runtime.store.StoreDump
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO


fun build_image(dump: StoreDump) : BufferedImage {
        val graph = toGraph(dump).toByteArray();

        try {
            val proc = ProcessBuilder(listOf("dot","-Tjpeg"))
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()

            proc.outputStream.buffered().use {
                it.write(graph)
            }
            return proc.inputStream.buffered().use {
                try {
                     ImageIO.read(it)
                } catch(ex: Exception){
                    println(graph);
                    throw ex
                }
            }

        } catch (ex: Exception) {
            println(graph)
            throw ex
        }

}

fun clamptext(st: String , len : Int = 18) : String {
    var label = st;
    if (label.length > len) {
        val cut = (len-3)/2;
        label = label.substring(0..cut) + "..." + label.substring((label.length - cut) until label.length)
    }
    return label
}

fun toGraph(dump : StoreDump) : String {

    val keys = hashSetOf<TaskKey>()
    val get_id = { key : TaskKey -> keys.add(key); key.hashCode()}
    val files = hashSetOf<Path>()
    val get_file = { key : Path -> files.add(key.toAbsolutePath()); key.toAbsolutePath().hashCode()}

    dump.taskReqs

    val taskReqs = dump.taskReqs.flatMap{ (e,v) -> v.map {  caller -> "${get_id(e)} -> ${get_id(caller.callee)} [arrowhead=dot,label=\"${clamptext(caller.stamp.toString())}\"]" } }
    val fileReqs = dump.fileReqs.flatMap { (e,v) -> v.map{ filereq -> "${get_id(e)} -> ${get_file(Paths.get(filereq.key.key.toString()))} [arrowhead=normal]"} }
    val fileProv = dump.fileGens.flatMap { (e,v) -> v.map{ fileProv -> "${get_id(e)} -> ${get_file(Paths.get(fileProv.key.key.toString()))} [arrowhead=veevee]"}}

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

    val key_labels = keys.map { k ->
        val misc = when (dump.observables.getOrDefault(k, Observability.Observed)) {
            Observability.Observed -> ""
            Observability.RootObserved -> ",color=purple"
            Observability.Detached -> ",fillcolor=grey"
        }

        """${k.hashCode()} [label="${k.id}(${clamptext(k.key.toString())})"${misc}]"""

    }
    val file_labels = files.map{ k ->
        val label = "[..]/${ k.parent.fileName }/${k.fileName }"
        """${k.hashCode()} [label="${label}"]"""}

    val result = """
                digraph G {
                    rankdir=TD
                    node [shape=box,fillcolor="#5e389b",fontcolor=white,style=filled]
                    ${key_labels.joinToString("\n")}
                    subgraph tmp {
                    rank=same
                    node [shape=note,fillcolor="#f3a432",style=filled,fontcolor=black]
                    ${file_labels.joinToString("\n")}
                    }
                    ${fileProv.joinToString("\n")}
                    ${taskReqs.joinToString("\n")}
                    ${fileReqs.joinToString("\n")}
                }
        """
    return result
}
