package com.thelastpickle.tlpcluster.containers

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.AccessMode
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.command.AttachContainerResultCallback
import com.github.dockerjava.core.command.EventsResultCallback
import com.github.dockerjava.core.command.LogContainerResultCallback
import com.thelastpickle.tlpcluster.Context
import java.io.*

class Terraform(val context: Context) {

    init {

    }

    fun execute(command: MutableList<String>) {
        val volumeLocal = Volume("/local")

        val cwdPath = System.getProperty("user.dir")

        val dockerContainer = context.docker.createContainerCmd("hashicorp/terraform")
                .withVolumes(volumeLocal)
                .withBinds(Bind(cwdPath, volumeLocal, AccessMode.rw))
                .withWorkingDir("/local")
                .withCmd(command)
                .withAttachStdout(true)
                .withAttachStdin(true)
                .exec()



        println("working dir is: $cwdPath")

        println("Starting Terraform container")

        context.docker.startContainerCmd(dockerContainer.id).exec()

        var containerState : InspectContainerResponse.ContainerState


        /**
         * https://github.com/docker-java/docker-java/issues/941
         *
         * try (PipedOutputStream out = new PipedOutputStream();
                PipedInputStream in = new PipedInputStream(out);

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {

            AttachContainerCmd attachContainerCmd = docker.attachContainerCmd(createContainerResponse.getId()).withStdIn(in)
                .withStdOut(true).withStdErr(true).withFollowStream(true);

            attachContainerCmd.exec(new AttachContainerResultCallback());

            String line = "Hello World!";
            while (!"q".equals(line)) {
            writer.write(line + "\n");

            writer.flush();

            line = reader.readLine();
            }
            } catch (Exception ex) {
            ex.printStackTrace();
        }

         */

        // attach a buffered reader for the stdin
        // write stuff to outPipe
//        val outPipe = PipedOutputStream()
//        val stdInput = PipedInputStream(outPipe)


        // dealing with standard output from the docker container
        val source = PipedOutputStream() // we're going to feed the frames to here
        val stdOutReader = PipedInputStream(source).bufferedReader()

        context.docker.attachContainerCmd(dockerContainer.id)
//                .withStdIn(stdInput)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .exec(object : AttachContainerResultCallback() {
                    override fun onNext(item: Frame?) {
                        if(item != null) {
                            source.write(item.payload)
                        }
                    }
            })


        // wait for container to start
        do {
            println("Starting terraform...")
            Thread.sleep(100)
            val state = context.docker.inspectContainerCmd(dockerContainer.id).exec().state
        } while(state.running != true)

        println("Started OK, input/output fun time")

        do {
            println(stdOutReader.readLine())
        } while(true)

        println("Reading line")
//        val line = reader.readLine()
//        println("Read line from standard input, sending to terraform: $line")


        do {
            Thread.sleep(500)
            containerState = context.docker.inspectContainerCmd(dockerContainer.id).exec().state


        } while (containerState.running == true)

        if (!containerState.status.equals("exited")) {
            println("Error in execution. Container exited with code : " + containerState.exitCode + ". " + containerState.error)
            return
        }

        println("Container execution completed")

        // clean up after ourselves
        context.docker.removeContainerCmd(dockerContainer.id)
                .withRemoveVolumes(true)
                .exec()
    }

    fun init() {
        return execute(mutableListOf("init", "/local"))
    }

    fun up() {
        return execute(mutableListOf("apply", "/local"))

    }

}