package com.thelastpickle.tlpcluster.containers

import com.github.dockerjava.api.model.AccessMode
import com.thelastpickle.tlpcluster.Context
import com.thelastpickle.tlpcluster.Docker
import com.thelastpickle.tlpcluster.ResourceFile
import com.thelastpickle.tlpcluster.VolumeMapping
import org.apache.commons.io.FileUtils
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.*

class CassandraUnpack(val context: Context,
                      val version: String,
                      val dest: Path,
                      val cacheLocation: Optional<Path> = Optional.empty()) {

    val docker = Docker(context)

    var cacheHits = 0
    var cacheChecks = 0

    fun download() {
        // example http://dl.bintray.com/apache/cassandra/pool/main/c/cassandra/cassandra_2.1.14_all.deb
        // if using a cache, check for the version
        var found = false
        val destination = File(dest.toFile(), getFileName())

        cacheLocation.map {
            cacheChecks++
            val tmp = File(it.toFile(), getFileName())
            if(tmp.exists()) {
                println("skipping download, using cache")
                FileUtils.copyFile(tmp, destination)
                found = true
                cacheHits++
            }
        }

        if(!found) {
            FileUtils.copyURLToFile(URL(getURL()), destination)
            // copy file over to the cache if we're using it
            cacheLocation.map {
                FileUtils.copyFile(destination, File(it.toFile(), getFileName()))
            }
        }
        File(dest.toFile(), "conf").mkdir()
    }



    fun extractConf(context: Context) : Result<String> {
        // required that the download have already run
        check(File(dest.toFile(), getFileName()).exists())

        val shellScript = ResourceFile(javaClass.getResource("unpack_cassandra.sh"))

        val volumes = mutableListOf(
                VolumeMapping(dest.toString(), "/working", AccessMode.rw),
                VolumeMapping(shellScript.path, "/unpack_cassandra.sh", AccessMode.ro)
        )

        return docker.runContainer("ubuntu",
                mutableListOf("sh", "/unpack_cassandra.sh", getFileName()),
                volumes,
                "/working/"
        )

    }

    fun getURL() = "http://dl.bintray.com/apache/cassandra/pool/main/c/cassandra/" + getFileName()
    fun getFileName() = "cassandra_${version}_all.deb"

}