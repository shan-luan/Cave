package com.lomekwi.cave.util

import java.io.File

class FileNameUtilTest extends munit.FunSuite {

  test("getExtension 返回最后一个点之后的文本") {
    assertEquals(FileNameUtil.getExtension(new File("a.txt")), "txt")
    assertEquals(FileNameUtil.getExtension(new File("archive.tar.gz")), "gz")
    assertEquals(FileNameUtil.getExtension(new File("dir/a.txt")), "txt")
  }

  test("无扩展名时 getExtension 返回空串") {
    assertEquals(FileNameUtil.getExtension(new File("noext")), "")
  }

  test("结尾是点时 getExtension 返回空串") {
    assertEquals(FileNameUtil.getExtension(new File("trailing.")), "")
  }

  test("输入为 null 时 getExtension 返回空串") {
    assertEquals(FileNameUtil.getExtension(null), "")
  }

  test("getExtension 把点文件视为扩展名") {
    assertEquals(FileNameUtil.getExtension(new File(".gitignore")), "gitignore")
  }

  test("ensureExtension 补上缺失的点") {
    assertEquals(FileNameUtil.ensureExtension("a", "txt"), "a.txt")
    assertEquals(FileNameUtil.ensureExtension("a", ".txt"), "a.txt")
  }

  test("ensureExtension 保留已有扩展名") {
    assertEquals(FileNameUtil.ensureExtension("a.txt", "txt"), "a.txt")
    assertEquals(FileNameUtil.ensureExtension("a.tar", "gz"), "a.tar.gz")
  }

  test("ensureExtension 匹配时忽略大小写") {
    assertEquals(FileNameUtil.ensureExtension("a.TXT", "txt"), "a.TXT")
  }

  test("ensureExtension 对 null 参数原样返回") {
    assert((FileNameUtil.ensureExtension(null: String, "txt")) == null)
    assertEquals(FileNameUtil.ensureExtension("a", null: String), "a")
  }

  test("File 重载的 ensureExtension 返回同一实例") {
    val file = new File("dir/a.txt")
    assert((file) eq (FileNameUtil.ensureExtension(file, "txt")))
  }

  test("File 重载的 ensureExtension 重建路径") {
    val result = FileNameUtil.ensureExtension(new File("dir/a"), "txt")
    assertEquals(result.getName, "a.txt")
    assertEquals(result.getParent, new File("dir").getPath)
  }

  test("文件为 null 时 ensureExtension 返回 null") {
    assert((FileNameUtil.ensureExtension(null: File, "txt")) == null)
  }
}
