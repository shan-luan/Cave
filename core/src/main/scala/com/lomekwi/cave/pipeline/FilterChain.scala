package com.lomekwi.cave.pipeline

import com.lomekwi.cave.project.{Id, IdSource}

@SerialVersionUID(1L)
final case class FilterChain(source: Source[?], entries: Vector[(Id, Filter[?])], defaults: Map[(Id, Int), Any]) {
  @transient @volatile private var indexCache: FilterChain.Index = null

  private def index: FilterChain.Index = {
    val cached = indexCache
    if (cached != null) {
      cached
    } else {
      synchronized {
        if (indexCache == null) {
          indexCache = FilterChain.buildIndex(source, entries)
        }
        indexCache
      }
    }
  }

  def filters: Vector[Filter[?]] = index.filters

  def isEmpty: Boolean = entries.isEmpty

  def lastFilter: Filter[?] = index.lastFilter

  def filterId(filter: Filter[?]): Id = index.idByFilter.getOrElse(filter, Id.noId())

  def append(filter: Filter[?])(using ids: IdSource): FilterChain = copy(entries = entries :+ (ids.nextId() -> filter))

  def insert(index: Int, filter: Filter[?])(using ids: IdSource): FilterChain = copy(entries = entries.patch(index, Seq(ids.nextId() -> filter), 0))

  def remove(filter: Filter[?]): FilterChain = copy(entries = entries.filterNot(_._2 eq filter))

  def replace(index: Int, filter: Filter[?]): FilterChain = copy(entries = entries.updated(index, (entries(index)._1, filter)))

  def move(filter: Filter[?], toIndex: Int): FilterChain = {
    val from = entries.indexWhere(_._2 eq filter)
    if (from < 0) {
      this
    } else {
      val without = entries.patch(from, Nil, 1)
      copy(entries = without.patch(toIndex, Seq(entries(from)), 0))
    }
  }

  def updateFilter(id: Id)(f: Filter[?] => Filter[?]): FilterChain = {
    val position = index.indexById.getOrElse(id, -1)
    if (position < 0) this else copy(entries = entries.updated(position, (id, f(entries(position)._2))))
  }

  def filterById(id: Id): Filter[?] = index.filterById.getOrElse(id, null)

  def replaceFilterById(id: Id, filter: Filter[?]): FilterChain = {
    val position = index.indexById.getOrElse(id, -1)
    if (position < 0) this else copy(entries = entries.updated(position, (id, filter)))
  }

  def defaultOf(port: Node.InPort[?]): Any = {
    val slot = index.ports.getOrElse(port, null)
    if (slot == null) {
      port.defaultData
    } else {
      defaults.getOrElse((slot.ownerId, slot.index), port.defaultData)
    }
  }

  def setDefault(port: Node.InPort[?], value: Any): FilterChain = {
    val slot = index.ports.getOrElse(port, null)
    if (slot == null) {
      this
    } else {
      copy(defaults = defaults.updated((slot.ownerId, slot.index), value))
    }
  }

  def setDefault(filterId: Id, portIndex: Int, value: Any): FilterChain = {
    copy(defaults = defaults.updated((filterId, portIndex), value))
  }

  def clear: FilterChain = copy(entries = Vector.empty)

  private[pipeline] def prev(in: Node.InPort[?]): Node.OutPort[?] = {
    val slot = index.ports.getOrElse(in, null)
    if (slot == null) null else slot.upstream
  }
}

object FilterChain {
  private[pipeline] final class Port(val ownerId: Id, val index: Int, val upstream: Node.OutPort[?])

  private[pipeline] final class Index(
      val filters: Vector[Filter[?]],
      val lastFilter: Filter[?],
      val indexById: Map[Id, Int],
      val filterById: Map[Id, Filter[?]],
      val idByFilter: Map[Filter[?], Id],
      val ports: Map[Node.InPort[?], Port])

  private def buildIndex(source: Source[?], entries: Vector[(Id, Filter[?])]): Index = {
    val filters = entries.map(_._2)
    val indexById = Map.newBuilder[Id, Int]
    val filterById = Map.newBuilder[Id, Filter[?]]
    val idByFilter = Map.newBuilder[Filter[?], Id]
    val ports = Map.newBuilder[Node.InPort[?], Port]

    source.inPorts.iterator.zipWithIndex.foreach { case (port, index) =>
      ports += port -> new Port(Id.noId(), index, null)
    }

    var upstream: Node.OutPort[?] = source.headOut
    entries.iterator.zipWithIndex.foreach { case ((id, filter), index) =>
      indexById += id -> index
      filterById += id -> filter
      idByFilter += (filter: Filter[?]) -> id
      filter.inPorts.iterator.zipWithIndex.foreach { case (port, portIndex) =>
        ports += port -> new Port(id, portIndex, if ((port eq filter.filterIn) && upstream != null) upstream else null)
      }
      upstream = filter.filterOut
    }

    new Index(filters, if (filters.isEmpty) null else filters.last, indexById.result(), filterById.result(), idByFilter.result(), ports.result())
  }

  def empty(source: Source[?]): FilterChain = FilterChain(source, Vector.empty, Map.empty)
}
