package com.lomekwi.cave.playback

sealed trait SeekEvent

case object SeekEvent extends SeekEvent
