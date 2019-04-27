package com.madurasoftware.vmnotifications.services

class DeviceWrapper(public val deviceType: DeviceType, public val name: String, public val address: String) : Comparable<DeviceWrapper> {

    enum class DeviceType {
        BLE, V4, NONE
    }
    override fun toString():String {
        return name
    }

    public override operator fun compareTo(other: DeviceWrapper): Int {
        return this.name.compareTo(other.name)
    }
}