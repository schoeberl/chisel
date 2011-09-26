package Chisel {

import IOdir._;
import Node._;
import ChiselError._

object Bits {

  def apply(x: Int): Bits = Lit(x){Bits()};
  def apply(x: Int, width: Int): Bits = Lit(x, width){Bits()};
  def apply(x: String): Bits = Lit(x){Bits()};
  def apply(x: String, width: Int): Bits = Lit(x, width){Bits()};
  
  def apply(width: Int = -1, dir: Symbol = null): Bits = {
    val res = new Bits();
    if(dir == null)
      res.dir = null;
    else if(dir.equals('input))
      res.dir = INPUT;
    else if(dir.equals('output))
      res.dir = OUTPUT;
    else
      println("INVALID DIRECTION");
    if(width > 0)
      res.init("", width);
    else 
      res.init("", widthOf(0))
    res
  }
  
  def apply(dir: Symbol): Bits = Bits(-1, dir) 
  
  def apply(): Bits = Bits(-1, null);
}

class Bits extends IO {
  override def toNode = this;
  override def fromNode(n: Node) = {
    val res = Bits('output).asInstanceOf[this.type];
    res assign n;
    res
  }
  def := (src: Bits) = {
    if(this.getClass != src.getClass) 
      ChiselErrors += TypeError(":=", this.getClass.toString, src.getClass.toString);
    if (comp == null)
      this.asInstanceOf[IO] assign src
    else
      comp assign src.toNode
  }

  def <==(src: Bits) = {
    if(this.getClass != src.getClass) 
      ChiselErrors += TypeError("<==", this.getClass.toString, src.getClass.toString);
    comp procAssign src.toNode;
  }

  override def apply(bit: Int): Bits = { Extract(this, bit){Bits()}};
  override def apply(hi: Int, lo: Int): Bits = {Extract(this, hi, lo){Bits()}};
  def apply(bit: UFix): Bits = Extract(this, bit){Bits()};
  def apply(hi: UFix, lo: UFix): Bits = Extract(this, hi, lo, -1){Bits()};

  def unary_-(): Bits = UnaryNodeCell(this, "-"){Bits()};
  def unary_~(): Bits = UnaryNodeCell(this, "~"){Bits()};
  def ===(b: Bits): Bool = LogicalNodeCell(this, b, "==="){Bits()};
  def != (b: Bits): Bool = LogicalNodeCell(this, b, "!="){Bits()};
  def << (b: Bits): Bits = BinaryNodeCell(this, b, "<<"){Bits()};
  def >> (b: Bits): Bits = BinaryNodeCell(this, b, ">>"){Bits()};
  def &  (b: Bits): Bits = BinaryNodeCell(this, b, "&"){Bits()};
  def |  (b: Bits): Bits = BinaryNodeCell(this, b, "|"){Bits()};
  def ^  (b: Bits): Bits = BinaryNodeCell(this, b, "^"){Bits()};
}

}