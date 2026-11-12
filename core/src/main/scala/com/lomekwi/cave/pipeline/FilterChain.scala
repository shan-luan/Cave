package com.lomekwi.cave.pipeline

import com.lomekwi.cave.project.{Id, IdSource}

@SerialVersionUID(1L)
final case class FilterChain(source: Source[?], entries: Vector[(Id, Filter[?])], defaults: Map[(Id, Int), Any]) {
  @transient @volatile private var filtersCache: Vector[Filter[?]] = null
  @transient @volatile private var lastFilterCache: Filter[?] = null
  @transient @volatile private var filterIdsCache: Map[Filter[?], Id] = null
  @transient @volatile private var filtersByIdCache: Map[Id, Filter[?]] = null
  @transient @volatile private var indexByIdCache: Map[Id, Int] = null
  @transient @volatile private var portKeysCache: Map[Node.InPort[?], (Id, Int)] = null
  @transient @volatile private var prevCache: Map[Node.InPort[?], Node.OutPort[?]] = null

  def filters: Vector[Filter[?]] = {
    val cached = filtersCache
    if (cached != null) {
      cached
    } else {
      synchronized {
        if (filtersCache == null) {
          filtersCache = entries.map(_._2)
        }
        filtersCache
      }
    }
  }

  def isEmpty: Boolean = entries.isEmpty

  def lastFilter: Filter[?] = {
    if (entries.isEmpty) {
      null
    } else {
      val cached = lastFilterCache
      if (cached != null) {
        cached
      } else {
        synchronized {
          if (lastFilterCache == null) {
            lastFilterCache = entries.last._2
          }
          lastFilterCache
        }
      }
    }
  }

  def filterId(filter: Filter[?]): Id = {
    val cached = filterIdsCache
    val ids = if (cached != null) {
      cached
    } else {
      synchronized {
        if (filterIdsCache == null) {
          filterIdsCache = entries.iterator.map { case (id, f) => (f: Filter[?]) -> id }.toMap
        }
        filterIdsCache
      }
    }
    ids.getOrElse(filter, Id.noId())
  }

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
    val index = indexById.getOrElse(id, -1)
    if (index < 0) this else copy(entries = entries.updated(index, (id, f(entries(index)._2))))
  }

  def filterById(id: Id): Filter[?] = {
    val cached = filtersByIdCache
    val byId = if (cached != null) {
      cached
    } else {
      synchronized {
        if (filtersByIdCache == null) {
          filtersByIdCache = entries.toMap
        }
        filtersByIdCache
      }
    }
    byId.getOrElse(id, null)
  }

  def replaceFilterById(id: Id, filter: Filter[?]): FilterChain = {
    val index = indexById.getOrElse(id, -1)
    if (index < 0) this else copy(entries = entries.updated(index, (id, filter)))
  }

  def defaultOf(port: Node.InPort[?]): Any = {
    portKeys.get(port) match {
      case Some((id, index)) => defaults.getOrElse((id, index), port.defaultData)
      case None => port.defaultData
    }
  }

  def setDefault(port: Node.InPort[?], value: Any): FilterChain = {
    portKeys.get(port) match {
      case Some((id, index)) => copy(defaults = defaults.updated((id, index), value))
      case None => this
    }
  }

  def setDefault(filterId: Id, portIndex: Int, value: Any): FilterChain = {
    copy(defaults = defaults.updated((filterId, portIndex), value))
  }

  def clear: FilterChain = copy(entries = Vector.empty)

  private[pipeline] def prev(in: Node.InPort[?]): Node.OutPort[?] = {
    prevByPort.getOrElse(in, null)
  }

  private def indexById: Map[Id, Int] = {
    val cached = indexByIdCache
    if (cached != null) {
      cached
    } else {
      synchronized {
        if (indexByIdCache == null) {
          indexByIdCache = entries.iterator.zipWithIndex.map { case ((id, _), index) => id -> index }.toMap
        }
        indexByIdCache
      }
    }
  }

  private def portKeys: Map[Node.InPort[?], (Id, Int)] = {
    val cached = portKeysCache
    if (cached != null) {
      cached
    } else {
      synchronized {
        if (portKeysCache == null) {
          val builder = Map.newBuilder[Node.InPort[?], (Id, Int)]
          source.inPorts.iterator.zipWithIndex.foreach { case (port, index) =>
            builder += port -> (Id.noId(), index)
          }
          entries.foreach { case (id, filter) =>
            filter.inPorts.iterator.zipWithIndex.foreach { case (port, index) =>
              builder += port -> (id, index)
            }
          }
          portKeysCache = builder.result()
        }
        portKeysCache
      }
    }
  }

  private def prevByPort: Map[Node.InPort[?], Node.OutPort[?]] = {
    val cached = prevCache
    if (cached != null) {
      cached
    } else {
      synchronized {
        if (prevCache == null) {
          val builder = Map.newBuilder[Node.InPort[?], Node.OutPort[?]]
          var upstream: Node.OutPort[?] = source.headOut
          entries.foreach { case (_, filter) =>
            if (filter.filterIn != null) {
              builder += filter.filterIn -> upstream
            }
            upstream = filter.filterOut
          }
          prevCache = builder.result()
        }
        prevCache
      }
    }
  }
}

object FilterChain {
  def empty(source: Source[?]): FilterChain = FilterChain(source, Vector.empty, Map.empty)
}
