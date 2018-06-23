package gameover.fwk.ai

import com.badlogic.gdx.math.{GridPoint2, MathUtils, Rectangle, Vector2}
import com.badlogic.gdx.utils.Array
import gameover.fwk.libgdx.utils.LibGDXHelper
import gameover.fwk.pool.{GridPoint2Pool, Vector2Pool}

import scala.math.Ordering

class AStar(hComputeStrategy: HComputeStrategy) extends LibGDXHelper {

  /**
   * This is the classical method to call to compute an a* path.
   * It will return tiles by tiles the path to use to reach the target. The vector
   * Notice that the path returned is really a tileset path. If you need a smooth
   * path to reach target, prefer method #findSmoothPath which is compute a smooth path from
   * the path computed by this method.
   * @param x the initial position on x axis
   * @param y the initial position on y axis
   * @param tx the target position on x axis
   * @param ty the target position on y axis
   * @param findClosestPoint return the nearest point if target is not reachable
   * @param collisionDetector the collision detector to use
   * @return an array of tiles indices to reach path. Returns <code>null</code> if node path is found.
   */
  def findPath(x: Float, y: Float, tx: Float, ty: Float, findClosestPoint: Boolean, collisionDetector: CollisionDetector): GdxArray[GridPoint2] = {
    val opened = new GdxArray[Point]()
    val closed = new GdxArray[Point]()
    val ret: GdxArray[GridPoint2] = new GdxArray[GridPoint2]
    val ix: Int = MathUtils.floor(x)
    val iy: Int = MathUtils.floor(y)
    val itx: Int = MathUtils.floor(tx)
    val ity: Int = MathUtils.floor(ty)
    if (ix == itx && iy == ity) {
      return ret
    }

    val nearest = new Nearest()
    var point: Option[Point] = addToOpenedIfElligible(ix, iy, itx, ity, collisionDetector, None, hComputeStrategy, opened, closed, nearest)
    if (point.isDefined) {
      point = processPath(itx, ity, collisionDetector, point.get, hComputeStrategy, opened, closed, nearest)
      if (point.isDefined) {
        return computeFinalPath(ix, iy, point.get)
      }
    }
    if (findClosestPoint && nearest.p != null) {
      computeFinalPath(ix, iy, nearest.p)
    } else {
      null
    }
  }

  /** Compute smooth path to a target point from an area.
   * If the center of the starting area is in the void, we look at all edges and search for each of
   * these edges  to find the shortest smart path.
   */
  def findSmoothPath(area: Rectangle, tx: Float, ty: Float, findClosestPoint: Boolean, collisionDetector: CollisionDetector): GdxArray[Vector2] = {
    val center = area.getCenter(Vector2Pool.obtain())
    val points = if (collisionDetector.checkPosition(center.x, center.y) == CollisionState.Void) {
      gameover.fwk.math.MathUtils.edges(area) filter { case (x, y) => collisionDetector.checkPosition(x, y) == CollisionState.Empty }
    } else (center.x, center.y) :: Nil
    val (x, y, path): (Float, Float, GdxArray[GridPoint2]) = points.map(p => (p, findPath(p._1, p._2, tx, ty, findClosestPoint, collisionDetector))).toList sortBy {
      case (_, pathPoints) => (if (pathPoints.last.x == tx && pathPoints.last.y == ty) 0 else 1, gameover.fwk.math.MathUtils.distanceSum(pathPoints.toArray()))
    } headOption match {
      case Some(((x, y), path)) => (x, y, path)
      case None => (0, 0, null)
    }

    if (path != null) {
      val smoothPath: GdxArray[Vector2] = new GdxArray[Vector2]
      computePointForSmoothPathAuxRecursively(area, path, 0, MathUtils.floor(x), MathUtils.floor(y), smoothPath)
      if (path.nonEmpty) {
        val last: GridPoint2 = path.peek()
        if (MathUtils.floor(tx) == last.x && MathUtils.floor(ty) == last.y) smoothPath.add(Vector2Pool.obtain(tx, ty))
      }
      Vector2Pool.free(center)
      smoothPath
    } else null
  }

  private def computePointForSmoothPathAuxRecursively(
                                                       area: Rectangle,
                                                       path: GdxArray[GridPoint2],
                                                       i: Int,
                                                       previousTileX: Int,
                                                       previousTileY: Int,
                                                       smoothPath: GdxArray[Vector2]) {
    if (i < path.lastIndex) {
      val tile: GridPoint2 = path.get(i)
      val nextTile: GridPoint2 = path.get(i + 1)
      val fromDiag: Boolean = tile.x != previousTileX && tile.y != previousTileY
      val fromLeft: Boolean = tile.x > previousTileX
      val toLeft: Boolean = tile.x > nextTile.x
      val fromBottom: Boolean = tile.y > previousTileY
      val toBottom: Boolean = tile.y > nextTile.y
      val fromRight: Boolean = tile.x < previousTileX
      val toRight: Boolean = tile.x < nextTile.x
      val fromEqualsX: Boolean = tile.x == previousTileX
      val toEqualsX: Boolean = tile.x == nextTile.x
      val fromTop: Boolean = tile.y < previousTileY
      val toTop: Boolean = tile.y < nextTile.y
      if (fromDiag) {
        if (toEqualsX) {
          if (toTop) {
            smoothPath.add(if (fromLeft) createBottomLeftPoint(area, tile) else createBottomRightPoint(area, tile))
          }
          else {
            smoothPath.add(if (fromLeft) createTopLeftPoint(area, tile) else createTopRightPoint(area, tile))
          }
        }
        else {
          if (toRight) {
            smoothPath.add(if (fromBottom) createBottomRightPoint(area, tile) else createTopRightPoint(area, tile))
          }
          else {
            smoothPath.add(if (fromBottom) createBottomLeftPoint(area, tile) else createTopLeftPoint(area, tile))
          }
        }
      }
      else {
        if (fromEqualsX) {
          if (fromTop) {
            smoothPath.add(if (toLeft) createTopLeftPoint(area, tile) else createTopRightPoint(area, tile))
          }
          else {
            smoothPath.add(if (toLeft) createBottomLeftPoint(area, tile) else createBottomRightPoint(area, tile))
          }
        }
        else {
          if (fromRight) {
            smoothPath.add(if (toBottom) createBottomRightPoint(area, tile) else createTopRightPoint(area, tile))
          }
          else {
            smoothPath.add(if (toBottom) createBottomLeftPoint(area, tile) else createTopLeftPoint(area, tile))
          }
        }
      }
      computePointForSmoothPathAuxRecursively(area, path, i + 1, tile.x, tile.y, smoothPath)
    }
  }

  private def createBottomLeftPoint(area: Rectangle, tile: GridPoint2): Vector2 = Vector2Pool.obtain(tile.x.toFloat + area.width/2, tile.y.toFloat + area.height/2)

  private def createBottomRightPoint(area: Rectangle, tile: GridPoint2): Vector2 = Vector2Pool.obtain(tile.x.toFloat + 1f - area.width/2, tile.y.toFloat + area.height/2)

  private def createTopLeftPoint(area: Rectangle, tile: GridPoint2): Vector2 = Vector2Pool.obtain(tile.x.toFloat + area.width/2, tile.y.toFloat + 1f - area.height/2)

  private def createTopRightPoint(area: Rectangle, tile: GridPoint2): Vector2 = Vector2Pool.obtain(tile.x.toFloat + 1f - area.width/2, tile.y.toFloat + 1f - area.height/2)

  private def computeFinalPath(ix: Int, iy: Int, p: Point): GdxArray[GridPoint2] = {
    var point: Point = p
    val ret: GdxArray[GridPoint2] = new Array[GridPoint2]
    for (i <- 0 until p.index) {
      ret.insert(0, GridPoint2Pool.obtain(point.x, point.y))
      point = point.ancestor.orNull
    }
    ret.insert(0, GridPoint2Pool.obtain(ix, iy))
    for (i <- ret.size-2 to 1 by -1) {
      val p: GridPoint2 = ret.get(i)
      val prevP: GridPoint2 = ret.get(i - 1)
      val nextP: GridPoint2 = ret.get(i + 1)
      if ((p.x == prevP.x && p.x == nextP.x) ||
          (p.y == prevP.y && p.y == nextP.y) ||
          (p.x == prevP.x + 1 && p.x + 1 == nextP.x && p.y == prevP.y + 1 && p.y + 1 == nextP.y) ||
          (p.x == prevP.x + 1 && p.x + 1 == nextP.x && p.y == prevP.y - 1 && p.y - 1 == nextP.y) ||
          (p.x == prevP.x - 1 && p.x - 1 == nextP.x && p.y == prevP.y + 1 && p.y + 1 == nextP.y) ||
          (p.x == prevP.x - 1 && p.x - 1 == nextP.x && p.y == prevP.y - 1 && p.y - 1 == nextP.y))
        ret.removeIndex(i)
    }
    ret.removeIndex(0)
    ret
  }

  class Nearest(var p : Point = null)

  private def processPath(tx: Int, ty: Int, collisionDetector: CollisionDetector, ancestor: Point,
                          hComputeStrategy: HComputeStrategy,
                          opened: GdxArray[Point], closed: GdxArray[Point],
                          nearest: Nearest): Option[Point] = {
    val x: Int = ancestor.x
    val y: Int = ancestor.y
    val a = Some(ancestor)
    addToOpenedIfElligible(x - 1, y, tx, ty, collisionDetector, a, hComputeStrategy, opened, closed, nearest)
    addToOpenedIfElligible(x, y - 1, tx, ty, collisionDetector, a, hComputeStrategy, opened, closed, nearest)
    addToOpenedIfElligible(x, y + 1, tx, ty, collisionDetector, a, hComputeStrategy, opened, closed, nearest)
    addToOpenedIfElligible(x + 1, y, tx, ty, collisionDetector, a, hComputeStrategy, opened, closed, nearest)
    if (ancestor.is(tx, ty)) {
      a
    } else {
      opened.drop(_ == ancestor)
      closed ::= ancestor
      if (opened.nonEmpty) {
        val lowest = opened.min(Ordering.by((p: Point)=>p.f))
        processPath(tx, ty, collisionDetector, lowest, hComputeStrategy, opened, closed, nearest)
      } else {
        None
      }
    }
  }

  private def addToOpenedIfElligible(x: Int, y: Int,
                                     targetX: Int, targetY: Int,
                                     collisionDetector: CollisionDetector,
                                     ancestor: Option[Point],
                                     hComputeStrategy: HComputeStrategy,
                                     opened: GdxArray[Point],
                                     closed: GdxArray[Point],
                                     nearest: Nearest): Option[Point] = {

    if (!closed.exists(_.is(x, y))) {
      if (!collisionDetector.checkCollision(x, y, onlyBlocking = false)) {
        val found: Option[Point] = opened.find(_.is(x, y))
        val g = ancestor match {
          case Some(a) if a.x == x => 10
          case Some(a) if a.y == y => 10
          case Some(a) => 14
          case _ => 0
        }
        val h = hComputeStrategy.h(x, y, targetX, targetY, collisionDetector)
        val point = found match {
          case Some(p) =>
            if (g + h < p.f) {
              p.g = g
              p.h = h
              p.ancestor = ancestor
            }
            p
          case None =>
            val p = new Point(x, y, g, h, ancestor)
            opened ::= p
            p
        }
        if (nearest.p == null || h < nearest.p.h || (h == nearest.p.h && g + h < nearest.p.f)) {
          nearest.p = point
        }
        return Some(point)
      }
    }
    None
  }
}
