package com.lomekwi.cave.playback

/** 播放头的运行状态。 */
enum PlayState {
  case Playing, Paused
  /** seek 的过渡态 */
  case Seeking
}
