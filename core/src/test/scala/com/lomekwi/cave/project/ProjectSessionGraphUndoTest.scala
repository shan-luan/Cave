package com.lomekwi.cave.project

import com.lomekwi.cave.pipeline.{NodeGraphFilter, Sink}
import com.lomekwi.cave.timeline.{Content, GdxTestBase, Instant, Interval, Segment, TestCont, Timespan}

class ProjectSessionGraphUndoTest extends GdxTestBase {
  given IdSource = TestIds

  test("节点图编辑进 dirty 与 undo/redo") {
    val project = new TestProject()
    try {
      val ngf = NodeGraphFilter()
      val segment: Segment = new TestCont(1000).attach(ngf)
      val filterId = segment.chain.filterId(ngf)
      val track = project.timeline.getTrackOrCreate(0)
      project.timeline.add(track, segment.asInstanceOf[Content], Interval(Instant.Zero, Instant.At(Timespan(1000))), Instant.Zero)

      val sink = ngf.innerNodes.collectFirst { case s: Sink => s }.get
      val next = ngf.withInnerNodes(ngf.innerNodes.setDefault(sink.inPorts.head, 1.0))
      val version = project.session.version

      project.session.commitGraph(Vector(NodeGraphFilter.InChain(segment.id, filterId)), next)

      val committed = project.session.timeline.segment(segment.id).get.chain.filterById(filterId)
      assert(committed eq next)
      assert(project.session.version == version + 1)
      assert(project.session.undo.canUndo)

      project.session.undo.undo()
      val restored = project.session.timeline.segment(segment.id).get.chain.filterById(filterId)
      assert(restored eq ngf)
      assert(project.session.version == version)

      project.session.undo.redo()
      val redone = project.session.timeline.segment(segment.id).get.chain.filterById(filterId)
      assert(redone eq next)
      assert(project.session.version == version + 1)
    } finally {
      project.close()
    }
  }
}
