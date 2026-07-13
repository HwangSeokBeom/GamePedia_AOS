package com.hwb.gamepedia.core.network

/** Loads canonical fixtures derived from GamePediaCoreServer 8790a13 contract tests. */
fun loadFixture(name: String): String =
    checkNotNull(object {}.javaClass.classLoader?.getResource("fixtures/$name")) {
        "Missing fixture: $name"
    }.readText()
