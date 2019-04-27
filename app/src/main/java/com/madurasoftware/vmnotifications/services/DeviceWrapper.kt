package com.madurasoftware.vmnotifications.services

class DeviceWrapper(val deviceType: DeviceType, val name: String, val address: String) : Comparable<DeviceWrapper> {

    enum class DeviceType {
        BLE, V4, NONE
    }
    override fun toString():String {
        return name
    }

    override operator fun compareTo(other: DeviceWrapper): Int {
        return this.name.compareTo(other.name)
    }
}