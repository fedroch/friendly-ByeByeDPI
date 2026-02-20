package io.github.fedroch.byedpi.services

import io.github.fedroch.byedpi.data.AppStatus
import io.github.fedroch.byedpi.data.Mode

var appStatus = AppStatus.Halted to Mode.VPN
    private set

fun setStatus(status: AppStatus, mode: Mode) {
    appStatus = status to mode
}
