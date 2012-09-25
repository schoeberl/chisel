package Chisel
import Node._
import scala.math._

object log2Up
{
  def apply(in: Int) = if(in == 1) 1 else ceil(log(in)/log(2)).toInt
}


object log2Down
{
  def apply(x : Int) = if (x == 1) 1 else floor(log(x)/log(2.0)).toInt
}


object isPow2
{
  def apply(in: Int) = in > 0 && ((in & (in-1)) == 0)
}

object LFSR16
{
  def apply(increment: Bool = Bool(true)) =
  {
    val width = 16
    val lfsr = Reg(resetVal = UFix(1, width))
    when (increment) { lfsr := Cat(lfsr(0)^lfsr(2)^lfsr(3)^lfsr(5), lfsr(width-1,1)).toUFix }
    lfsr
  }
}

object PopCount
{
  def apply(in: Seq[Bool]): UFix = {
    if (in.size == 0)
      UFix(0)
    else if (in.size == 1)
      in(0)
    else
      apply(in.slice(0, in.size/2)) + Cat(Bits(0), apply(in.slice(in.size/2, in.size)))
  }
  def apply(in: Bits): UFix = apply((0 until in.getWidth).map(in(_).toBool))
}

object Reverse
{
  def doit(in: Bits, base: Int, length: Int): Bits =
  {
    val half = (1 << log2Up(length))/2
    if (length == 1)
      in(base)
    else
      Cat(doit(in, base, half), doit(in, base+half, length-half))
  }
  def apply(in: Bits) = doit(in, 0, in.getWidth)
}


object ShiftRegister
{
  def apply [T <: Data](n: Int, in: T, en: Bool = Bool(true)): T =
  {
    if (n == 1)
    {
      val res = Reg() { in.clone }
      when (en)
      {
        res := in
      }
      res
    }
    else
    {
      Reg(apply(n-1, in, en))
    }
  }
}

object UFixToOH
{
  def apply(in: UFix, width: Int = -1): Bits =
  {
    if (width == -1)
      UFix(1) << in
    else
      UFix(1) << in(log2Up(width)-1,0)
  }
}

object Mux1H 
{
  def buildMux[T <: Data](sel: Bits, in: Seq[T], i: Int, n: Int): T = {
    if (n == 1)
      in(i)
    else
    {
      val half_n = (1 << log2Up(n))/2
      val left = buildMux(sel, in, i, half_n)
      val right = buildMux(sel, in, i + half_n, n - half_n)
      Mux(sel(i+n-1,i+half_n).orR, right, left)
    }
  }

  def apply [T <: Data](sel: Bits, in: Seq[T]): T = buildMux(sel, in, 0, in.size)
  def apply [T <: Data](sel: Seq[Bool], in: Seq[T]): T = buildMux(Cat(Bits(0),sel.reverse:_*), in, 0, in.size)
}


object OHToUFix
{
  def apply(in: Seq[Bits]): UFix = {
    if (in.size <= 1) return UFix(0)
    if (in.size == 2) return in(1)
    val hi = in.slice(in.size/2, in.size)
    val lo = in.slice(0, in.size/2)
    Cat(hi.reduceLeft(_||_), apply(hi zip lo map { case (x, y) => x || y }))
  }
  def apply(in: Bits): UFix = apply((0 until in.getWidth).map(in(_)))
}


class PipeIO[+T <: Data]()(data: => T) extends Bundle
{
  val valid = Bool(OUTPUT)
  val bits = data.asOutput
  def fire(dummy: Int = 0) = valid
  override def clone =
    try {
      super.clone()
    } catch {
      case e: java.lang.Exception => {
        new PipeIO()(data).asInstanceOf[this.type]
      }
    }
}

class FIFOIO[T <: Data]()(data: => T) extends Bundle
{
  val ready = Bool(INPUT)
  val valid = Bool(OUTPUT)
  val bits  = data.asOutput
  def fire(dummy: Int = 0) = ready && valid
  override def clone =
    try {
      super.clone()
    } catch {
      case e: java.lang.Exception => {
        new FIFOIO()(data).asInstanceOf[this.type]
      }
    }
}

class REGIO[T <: Data]()(data: => T) extends Bundle
{
	val bits = data.asOutput
}

class EnqIO[T <: Data]()(data: => T) extends FIFOIO()(data) 
{
  def enq(dat: T): T = { valid := Bool(true); bits := dat; dat }
  valid := Bool(false);
  for (io <- bits.flatten.map(x => x._2))
    io := UFix(0)
  override def clone = { new EnqIO()(data).asInstanceOf[this.type]; }
}

class DeqIO[T <: Data]()(data: => T) extends FIFOIO()(data) 
{
  flip()
  ready := Bool(false);
  def deq(b: Boolean = false): T = { ready := Bool(true); bits }
  override def clone = { new DeqIO()(data).asInstanceOf[this.type]; }
}


class FIFOIOC[+T <: Data]()(data: => T) extends Bundle
{
  val ready = Bool(INPUT)
  val valid = Bool(OUTPUT)
  val bits  = data.asOutput
}


class ioArbiter[T <: Data](n: Int)(data: => T) extends Bundle {
  val in  = Vec(n) { (new FIFOIO()) { data } }.flip
  val out = (new FIFOIO()) { data }
  val chosen = Bits(OUTPUT, log2Up(n))
}

object foldR
{
  def apply[T <: Bits](x: Seq[T])(f: (T, T) => T): T =
    if (x.length == 1) x(0) else f(x(0), foldR(x.slice(1, x.length))(f))
}

object ArbiterCtrl
{
  def apply(request: Seq[Bool]) = {
    Bool(true) +: (1 until request.length).map(i => !request.slice(0, i).foldLeft(Bool(false))(_ || _))
  }
}

class Arbiter[T <: Data](n: Int)(data: => T) extends Component {
  val io = new ioArbiter(n)(data)

  val grant = ArbiterCtrl(io.in.map(_.valid))
  (0 until n).map(i => io.in(i).ready := grant(i) && io.out.ready)

  var dout = io.in(n-1).bits
  var choose = Bits(n-1)
  for (i <- n-2 to 0 by -1) {
    dout = Mux(io.in(i).valid, io.in(i).bits, dout)
    choose = Mux(io.in(i).valid, Bits(i), choose)
  }

  io.out.valid := io.in.map(_.valid).foldLeft(Bool(false))(_ || _)
  io.out.bits <> dout
  io.chosen := choose
}

class RRArbiter[T <: Data](n: Int)(data: => T) extends Component {
  val io = new ioArbiter(n)(data)

  val last_grant = Reg(resetVal = Bits(0, log2Up(n)))
  val g = ArbiterCtrl((0 until n).map(i => io.in(i).valid && UFix(i) > last_grant) ++ io.in.map(_.valid))
  val grant = (0 until n).map(i => g(i) && UFix(i) > last_grant || g(i+n))
  (0 until n).map(i => io.in(i).ready := grant(i) && io.out.ready)

  var choose = Bits(n-1)
  for (i <- n-2 to 0 by -1)
    choose = Mux(io.in(i).valid, Bits(i), choose)
  for (i <- n-1 to 1 by -1)
    choose = Mux(io.in(i).valid && UFix(i) > last_grant, Bits(i), choose)
  when (io.out.valid && io.out.ready) {
    last_grant := choose
  }

  val dvec = Vec(n) { data } 
  (0 until n).map(i => dvec(i) := io.in(i).bits )

  io.out.valid := io.in.map(_.valid).foldLeft(Bool(false))( _ || _)
  io.out.bits := dvec(choose)
  io.chosen := choose
}
