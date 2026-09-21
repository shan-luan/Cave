package com.lomekwi.cave.util

import org.junit.jupiter.api.Assertions.{assertEquals, assertNull, assertSame}
import org.junit.jupiter.api.Test

import java.io.File

class FileNameUtilTest {

  @Test
  def getExtension_returnsTextAfterLastDot(): Unit = {
    assertEquals("txt", FileNameUtil.getExtension(new File("a.txt")))
    assertEquals("gz", FileNameUtil.getExtension(new File("archive.tar.gz")))
    assertEquals("txt", FileNameUtil.getExtension(new File("dir/a.txt")))
  }

  @Test
  def getExtension_withoutDotReturnsEmpty(): Unit = {
    assertEquals("", FileNameUtil.getExtension(new File("noext")))
  }

  @Test
  def getExtension_trailingDotReturnsEmpty(): Unit = {
    assertEquals("", FileNameUtil.getExtension(new File("trailing.")))
  }

  @Test
  def getExtension_nullReturnsEmpty(): Unit = {
    assertEquals("", FileNameUtil.getExtension(null))
  }

  @Test
  def getExtension_dotfileIsTreatedAsExtension(): Unit = {
    assertEquals("gitignore", FileNameUtil.getExtension(new File(".gitignore")))
  }

  @Test
  def ensureExtension_appendsMissingDot(): Unit = {
    assertEquals("a.txt", FileNameUtil.ensureExtension("a", "txt"))
    assertEquals("a.txt", FileNameUtil.ensureExtension("a", ".txt"))
  }

  @Test
  def ensureExtension_keepsExistingExtension(): Unit = {
    assertEquals("a.txt", FileNameUtil.ensureExtension("a.txt", "txt"))
    assertEquals("a.tar.gz", FileNameUtil.ensureExtension("a.tar", "gz"))
  }

  @Test
  def ensureExtension_matchesCaseInsensitively(): Unit = {
    assertEquals("a.TXT", FileNameUtil.ensureExtension("a.TXT", "txt"))
  }

  @Test
  def ensureExtension_nullArgumentsPassThrough(): Unit = {
    assertNull(FileNameUtil.ensureExtension(null: String, "txt"))
    assertEquals("a", FileNameUtil.ensureExtension("a", null: String))
  }

  @Test
  def ensureExtension_fileVersionKeepsSameInstance(): Unit = {
    val file = new File("dir/a.txt")
    assertSame(file, FileNameUtil.ensureExtension(file, "txt"))
  }

  @Test
  def ensureExtension_fileVersionRebuildsPath(): Unit = {
    val result = FileNameUtil.ensureExtension(new File("dir/a"), "txt")
    assertEquals("a.txt", result.getName)
    assertEquals(new File("dir").getPath, result.getParent)
  }

  @Test
  def ensureExtension_nullFileReturnsNull(): Unit = {
    assertNull(FileNameUtil.ensureExtension(null: File, "txt"))
  }
}
