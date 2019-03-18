package com.madurasoftware.vmnotifications.services

import java.util.concurrent.LinkedBlockingQueue

object NotificationQueue : LinkedBlockingQueue<String>() {

}