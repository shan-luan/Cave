package com.lomekwi.cave.resource
//TODO:所有Resource管理的资源应该在超时后自动释放，并在下次访问时重建。
trait Resource extends AutoCloseable {
}
