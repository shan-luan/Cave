package com.lomekwi.cave.timeline.playback

sealed trait SeekEvent

case object SeekEvent extends SeekEvent
