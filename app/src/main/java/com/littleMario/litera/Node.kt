package com.littleMario.litera

class Node(
    val next: IntArray = IntArray(35) { -1 },
    var terminal: Boolean = false
)