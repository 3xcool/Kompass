package com.tekmoon.kompass.util

import platform.Foundation.NSUUID

actual fun randomUUID(): String = NSUUID().UUIDString()