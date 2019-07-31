package com.thelastpickle.tlpcluster.commands

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.ajalt.mordant.TermColors
import com.thelastpickle.tlpcluster.Context
import com.thelastpickle.tlpcluster.commands.converters.AZConverter
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import java.io.File
import org.apache.commons.io.FileUtils
import com.thelastpickle.tlpcluster.terraform.Configuration
import com.thelastpickle.tlpcluster.containers.Terraform
import org.apache.logging.log4j.kotlin.logger
import java.util.zip.GZIPInputStream


sealed class CopyResourceResult {
    class Created(val fp: File) : CopyResourceResult()
    class Existed(val fp: File) : CopyResourceResult()
}

@Parameters(commandDescription = "Initialize this directory for tlp-cluster")
class Init(val context: Context) : ICommand {


    @Parameter(description = "Client, Ticket, Purpose", required = true, arity = 3)
    var tags = mutableListOf<String>()

    @Parameter(description = "Number of Cassandra instances", names = ["--cassandra", "-c"])
    var cassandraInstances = 3

    @Parameter(description = "Number of stress instances", names = ["--stress", "-s"])
    var stressInstances = 0

    @Parameter(description = "Start instances automatically", names = ["--up"])
    var start = false

    @Parameter(description = "Instance Type", names = ["--instance"])
    var instanceType =  "r3.2xlarge"

    @Parameter(description = "Limit to specified availability zones", names = ["--azs", "--az", "-z"], listConverter = AZConverter::class)
    var azs: List<String> = listOf()

    override fun execute() {
        println("Initializing directory")

        val client = tags[0]
        val ticket = tags[1]
        val purpose = tags[2]

        check(client.isNotBlank())
        check(ticket.isNotBlank())
        check(purpose.isNotBlank())

        val allowedTypes = listOf("m1", "m3", "t1", "c1", "c3", "cc2", "cr1", "m2", "r3", "d2", "hs1", "i2", "c5", "m5")

        var found = false
        for(x in allowedTypes) {
            if(instanceType.startsWith(x))
                found = true
        }
        if(!found) {
            throw Exception("You requested the instance type $instanceType, but unfortunately it isn't supported in EC2 Classic.  We currently only support the following classes: $allowedTypes")
        }

        // Added because if we're reusing a directory, we don't want any of the previous state
        Clean().execute()

        // copy provisioning over

        println("Copying provisioning files")


        var config = initializeDirectory(client, ticket, purpose)


        config.numCassandraInstances = cassandraInstances
        config.numStressInstances = stressInstances
        config.cassandraInstanceType = instanceType

        config.setVariable("client", client)
        config.setVariable("ticket", ticket)
        config.setVariable("purpose", purpose)

        if(azs.isNotEmpty()) {
            println("Overriding default az list with $azs")
            config.azs = expand(context.userConfig.region, azs)
        }

        writeTerraformConfig(config)

        println("Your workspace has been initialized with $cassandraInstances Cassandra instances (${config.cassandraInstanceType}) and $stressInstances stress instances in ${context.userConfig.region}")

        if(start) {
            Up(context).execute()
        } else {
            with(TermColors()) {
                println("Next you'll want to run ${green("tlp-cluster up")} to start your instances.")
            }
        }

    }


    fun initializeDirectory(client: String, ticket: String, purpose: String) : Configuration {
        val reflections = Reflections("com.thelastpickle.tlpcluster.commands.origin", ResourcesScanner())
        val provisioning = reflections.getResources(".*".toPattern())

        for (f in provisioning) {
            val input = this.javaClass.getResourceAsStream("/" + f)
            val outputFile = f.replace("com/thelastpickle/tlpcluster/commands/origin/", "")

            val output = File(outputFile)
            println("Writing ${output.absolutePath}")

            output.absoluteFile.parentFile.mkdirs()
            FileUtils.copyInputStreamToFile(input, output)
        }

        // gunzip the collector
        val collector = "collector-0.11.1-SNAPSHOT.jar.gz"

        val dir = "provisioning/cassandra/"

        println("Copying JMX collector")

        val fp = GZIPInputStream(File(dir, collector).inputStream())

        val out = File(dir, collector.removeSuffix(".gz"))

        out.writeBytes(fp.readBytes())


        return Configuration(ticket, client, purpose, context.userConfig.region , context = context)
    }

    fun writeTerraformConfig(config: Configuration): Result<String> {
        val configOutput = File("terraform.tf.json")
        config.write(configOutput)

        val terraform = Terraform(context)
        return terraform.init()
    }



    companion object {
        fun expand(region: String, azs: List<String>) : List<String> = azs.map { region + it }

        val log = logger()


    }


}