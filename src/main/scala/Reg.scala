// author: jonathan bachrach
package Chisel {

import Node._;
import Reg._;
import ChiselError._;

object Reg {
  def regWidth(w: Int) = {
    if(w <= 0)
      maxWidth _;
    else 
      fixWidth(w)
  }
  val noInit = Lit(0){Fix()};
  def apply[T <: Data](data: T, width: Int, resetVal: T)(gen: => T): T = {
    val dataIn: T = 
      (if(data == null) 
	data 
       else if(gen != null) 
	 gen
       else {
	 ChiselErrors += IllegalState("no type specified for Reg", 4)
	 Bits().asInstanceOf[T]
       })
    val regCell = new RegCell[T](dataIn, width, data != null, resetVal != null)(gen);
    if(data != null) regCell.io.data <> data;
    if(resetVal != null) regCell.io.resetVal <> resetVal;
    regCell.io.q
  }

  def apply[T <: Data](data: T): T = Reg[T](data, -1, null.asInstanceOf[T]){data.clone}

  def apply[T <: Data](data: T, resetVal: T): T = Reg[T](data, -1, resetVal){data.clone}

  def apply[T <: Data](width: Int = -1, resetVal: T): T = Reg[T](null.asInstanceOf[T], width, resetVal){resetVal.clone}

  def apply[T <: Data]()(gen: => T): T = Reg[T](null.asInstanceOf[T], -1, null.asInstanceOf[T])(gen)

}


class RegCell[T <: Data](d: T, w: Int, hasInput: Boolean, isReset: Boolean)(gen: => T) extends Cell {
  val io = new Bundle(){
    val data     = gen.asInput();
    val resetVal = gen.asInput();
    val q        = gen.asOutput();
  }
  io.setIsCellIO;
  val primitiveNode = new Reg();
  val dInput: Node = if(hasInput) io.data.toNode else null;
  if(isReset)
    primitiveNode.init("", regWidth(w), dInput, io.resetVal.toNode);
  else
    primitiveNode.init("", regWidth(w), dInput);
  val fb = io.q.fromNode(primitiveNode).asInstanceOf[T] 
  fb.setIsCellIO;
  fb ^^ io.q;
  io.q.comp = primitiveNode.asInstanceOf[Reg];
  io.q.isRegOut = true;
}

class Reg extends Delay with proc{
  def updateVal = inputs(0);
  def resetVal  = inputs(1);
  def isReset  = inputs.length == 2;
  def isUpdate = !(updateVal == null);
  def update (x: Node) = { inputs(0) = x };
  var assigned = false;
  def procAssign(src: Node) = {
    if (assigned)
      ChiselErrors += IllegalState("reassignment to Reg", 3);
    var res = Bool(true);
    for (i <- 0 until conds.length) {
      res = conds(i) && res;
    }
    // println(this.name + " <== " + res + " " + conds.length);
    // val res = conds.foldRight(Lit(1,1)){(a, b) => a&&b}
    updates.push((res, src));
  }
  def nameOpt: String = if (name.length > 0) name else "REG"
  override def toString: String = {
    if (component == null) return "nullcompreg";
    if (component.isWalking.contains(this)) 
      nameOpt
    else {
      component.isWalking += this;
      var res = nameOpt + "(";
      if (isUpdate) res = res + " " + updateVal;
      if (isReset)  res = res + " " + resetVal;
      res += ")";
      component.isWalking -= this;
      res;
    }
  }
  override def assign(src: Node) = {
    if(assigned || inputs(0) != null)
      ChiselErrors += IllegalState("reassignment to Reg", 3);
    else { assigned = true; super.assign(src)}
  }
  override def emitRefV: String = if (name == "") "R" + emitIndex else name;
  override def emitDef: String = "";
  override def emitReg: String =
    "    " + emitRef + " <= " + 
    (if (isReset) "reset ? " + resetVal.emitRef + " : " else "" ) + 
    updateVal.emitRef + ";\n"
  override def emitDec: String = 
    "  reg[" + (width-1) + ":0] " + emitRef + ";\n";

  override def emitDefLoC: String = 
    "  " + emitRef + "_shadow = " + 
    (if (isReset) "mux<" + width + ">(reset, " + resetVal.emitRef + ", " else "") + 
    updateVal.emitRef + (if (isReset) ");\n" else ";\n");
  override def emitDefHiC: String =
    "  " + emitRef + " = " + emitRef + "_shadow;\n";
  override def emitDecC: String = 
    "  dat_t<" + width + "> " + emitRef + ";\n" +
    "  dat_t<" + width + "> " + emitRef + "_shadow;\n";
}

}