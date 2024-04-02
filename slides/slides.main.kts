#!/usr/bin/env kotlin

@file:DependsOn("com.hexagonkt:http_server_netty:3.5.3")

import com.hexagonkt.core.logging.info
import com.hexagonkt.core.logging.logger
import com.hexagonkt.http.server.netty.serve
import com.hexagonkt.http.server.callbacks.FileCallback
import java.nio.file.Path

val currentPath: Path = Path.of(System.getProperty("user.dir")).info("Current path: ")

serve {
    get("/*", FileCallback(currentPath.toFile()))
    get(callback = FileCallback(currentPath.resolve("revealjs.html").toFile()))
}

logger.info { "SCRIPT STARTED" }
