package com.lomekwi.cave.util

import java.io.File
import java.nio.file.Files

class MimeTypeTest extends munit.FunSuite {

  test("输入为 null 时 MIME 类型检测返回 null") {
    assert((MimeType.detectMimeType(null)) == null)
  }

  test("文件不存在时 MIME 类型检测返回 null") {
    assert((MimeType.detectMimeType(new File("/nonexistent/nowhere.mp4"))) == null)
  }

  test("MIME 类型检测回退到扩展名") {
    val path = Files.createTempFile("cave-mimetype", ".cave")
    try {
      assertEquals(MimeType.detectMimeType(path.toFile), "application/x-cave-project")
    } finally {
      Files.deleteIfExists(path)
    }
  }

  test("扩展名未映射时 MIME 类型检测返回 null") {
    val path = Files.createTempFile("cave-mimetype", ".zzz")
    try {
      assert((MimeType.detectMimeType(path.toFile)) == null)
    } finally {
      Files.deleteIfExists(path)
    }
  }

  test("通配符按斜杠切分") {
    assertEquals(MimeType.getTypeWildcard("video/mp4"), "video/*")
    assertEquals(MimeType.getSubtypeWildcard("video/mp4"), "*/mp4")
    assertEquals(MimeType.getAllWildcard, "*/*")
  }

  test("类型通配符拒绝 null") {
    intercept[IllegalArgumentException] { MimeType.getTypeWildcard(null) }
  }

  test("类型通配符拒绝空串") {
    intercept[IllegalArgumentException] { MimeType.getTypeWildcard("") }
  }

  test("类型通配符拒绝缺少斜杠") {
    intercept[IllegalArgumentException] { MimeType.getTypeWildcard("video") }
  }

  test("子类型通配符拒绝缺少斜杠") {
    intercept[IllegalArgumentException] { MimeType.getSubtypeWildcard("video") }
  }
}
