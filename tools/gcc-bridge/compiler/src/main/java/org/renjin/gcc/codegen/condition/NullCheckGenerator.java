package org.renjin.gcc.codegen.condition;

import org.renjin.gcc.codegen.MethodGenerator;
import org.renjin.gcc.codegen.expr.PtrExpr;
import org.renjin.gcc.gimple.GimpleOp;
import org.renjin.repackaged.asm.Label;


public class NullCheckGenerator implements ConditionGenerator {

  private final GimpleOp op;
  private final PtrExpr ptrExpr;

  public NullCheckGenerator(GimpleOp op, PtrExpr ptrExpr) {
    this.op = op;
    this.ptrExpr = ptrExpr;
  }

  @Override
  public final void emitJump(MethodGenerator mv, Label trueLabel, Label falseLabel) {
    switch (op) {
      case EQ_EXPR:
        // "If ptrExpr is  NULL, then the condition is TRUE"
        ptrExpr.jumpIfNull(mv, trueLabel);
        mv.goTo(falseLabel);
        break;
      case NE_EXPR:
        // "If ptrExpr is NOT NULL, then the condition is TRUE"
        ptrExpr.jumpIfNull(mv, falseLabel);
        mv.goTo(trueLabel);
        break;
      
      default:
        throw new UnsupportedOperationException("op: " + op);
    }
  }
}
