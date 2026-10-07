package app.haven.companion.core

import java.io.File

/** Finds a file in the repository by walking up from the test's working directory. */
fun repoFile(path: String): File {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        val f = File(dir, path)
        if (f.exists()) return f
        dir = dir.parentFile
    }
    error("$path not found")
}

class InMemoryPersistence(var stored: String? = null) : Persistence {
    var saves = 0
    override fun load() = stored
    override fun save(json: String) { stored = json; saves++ }
    override fun clear() { stored = null }
}

fun knowledgeFromRepo(): KnowledgeBase =
    KnowledgeBase(repoFile("knowledge/sources").listFiles()!!.filter { it.extension == "md" }.sortedBy { it.name }
        .map { it.name to it.readText() })
