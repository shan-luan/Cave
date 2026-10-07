package com.lomekwi.cave.playback

/** 播放头的运行状态。 */
enum PlayState {
  case Playing, Paused
  /** seek 的过渡态，等所有轨道 sync 到目标位置后恢复 seek 前的状态 */
  case Seeking
}
