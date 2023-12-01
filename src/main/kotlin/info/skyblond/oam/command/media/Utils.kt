package info.skyblond.oam.command.media

import com.github.ajalt.clikt.core.CliktCommand
import info.skyblond.oam.readLTFSStartBlock
import info.skyblond.oam.walkFile
import java.nio.file.Path

internal fun CliktCommand.listFiles(path: Path): List<Path> {
    val listOfPaths = buildList { path.walkFile { add(it) } }
    return if (listOfPaths.isNotEmpty() && listOfPaths[0].readLTFSStartBlock() != null) {
        echo("[I]Reading LTFS attr...")
        listOfPaths.sortedBy { p ->
            p.readLTFSStartBlock() ?: error("Missing attr `list.startblock`: $p")
        }
    } else {// not ltfs
        echo("[W]Failed to fetch LTFS attr", err = true)
        listOfPaths
    }
}
