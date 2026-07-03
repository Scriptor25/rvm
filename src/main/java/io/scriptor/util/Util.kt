package io.scriptor.util

import io.scriptor.machine.Device
import io.scriptor.machine.IODevice

fun checkDeviceOverlap(devices: Array<Device>) {
    for (j in devices.indices) {
        val b = devices[j]
        if (b is IODevice) for (i in j + 1..<devices.size) {
            val a = devices[i]
            if (a is IODevice) {
                if (a.begin < b.end && b.begin < a.end) {
                    Log.warn(
                        "device overlap: %s [%08x;%08x] and %s [%08x;%08x]",
                        b,
                        b.begin,
                        b.end,
                        a,
                        a.begin,
                        a.end,
                    )
                }
            }
        }
    }
}
