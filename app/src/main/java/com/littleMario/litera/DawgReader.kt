package com.littleMario.litera

import java.io.DataInputStream

class DawgReader {

    fun load(input: DataInputStream): Array<Node> {

        val nodeCount = input.readInt()

        val nodes = Array(nodeCount) {
            Node()
        }

        for (i in 0 until nodeCount) {

            val terminal = input.readBoolean()
            nodes[i].terminal = terminal

            val transitions = input.readInt()

            repeat(transitions) {

                val charIndex = input.readInt()
                val childId = input.readInt()

                nodes[i].next[charIndex] = childId
            }
        }

        return nodes
    }
}