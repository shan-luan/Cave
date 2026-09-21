package com.lomekwi.cave.util

import org.junit.jupiter.api.Assertions.{assertEquals, assertNull, assertThrows}
import org.junit.jupiter.api.Test

import java.io.File
import java.nio.file.Files

class MimeTypeTest {

  @Test
  def detectMimeType_nullReturnsNull(): Unit = {
    assertNull(MimeType.detectMimeType(null))
  }

  @Test
  def detectMimeType_missingFileReturnsNull(): Unit = {
    assertNull(MimeType.detectMimeType(new File("/nonexistent/nowhere.mp4")))
  }

  @Test
  def detectMimeType_fallsBackToExtension(): Unit = {
    val path = Files.createTempFile("cave-mimetype", ".cave")
    try {
      assertEquals("application/x-cave-project", MimeType.detectMimeType(path.toFile))
    } finally {
      Files.deleteIfExists(path)
    }
  }

  @Test
  def detectMimeType_unmappedExtensionReturnsNull(): Unit = {
    val path = Files.createTempFile("cave-mimetype", ".zzz")
    try {
      assertNull(MimeType.detectMimeType(path.toFile))
    } finally {
      Files.deleteIfExists(path)
    }
  }

  @Test
  def wildcards_splitAtSlash(): Unit = {
    assertEquals("video/*", MimeType.getTypeWildcard("video/mp4"))
    assertEquals("*/mp4", MimeType.getSubtypeWildcard("video/mp4"))
    assertEquals("*/*", MimeType.getAllWildcard)
  }

  @Test
  def typeWildcard_rejectsNull(): Unit = {
    assertThrows(classOf[IllegalArgumentException], () => MimeType.getTypeWildcard(null))
  }

  @Test
  def typeWildcard_rejectsEmpty(): Unit = {
    assertThrows(classOf[IllegalArgumentException], () => MimeType.getTypeWildcard(""))
  }

  @Test
  def typeWildcard_rejectsMissingSlash(): Unit = {
    assertThrows(classOf[IllegalArgumentException], () => MimeType.getTypeWildcard("video"))
  }

  @Test
  def subtypeWildcard_rejectsMissingSlash(): Unit = {
    assertThrows(classOf[IllegalArgumentException], () => MimeType.getSubtypeWildcard("video"))
  }
}
