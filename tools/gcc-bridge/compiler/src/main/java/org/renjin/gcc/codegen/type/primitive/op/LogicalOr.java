package org.renjin.gcc.codegen.type.primitive.op;

import org.renjin.gcc.codegen.MethodGenerator;
import org.renjin.gcc.codegen.expr.JExpr;
import org.renjin.repackaged.asm.Label;
import org.renjin.repackaged.asm.Opcodes;
import org.renjin.repackaged.asm.Type;

import javax.annotation.Nonnull;

/**
 * Logical binary operator, such as TRUTH_OR, TRUTH_AND
 */
public class LogicalOr implements JExpr {
  
  private JExpr x;
  private JExpr y;

  public LogicalOr(JExpr x, JExpr y) {
    this.x = x;
    this.y = y;
  }

  @Nonnull
  @Override
  public Type getType() {
    return Type.BOOLEAN_TYPE;
  }

  @Override
  public void load(@Nonnull MethodGenerator mv) {
    Label trueLabel = new Label();
    Label exitLabel = new Label();
    
    x.load(mv);
    
    // if x is true, then can jump right away to true
    jumpIfTrue(mv, trueLabel);

    // Otherwise need to check y
    y.load(mv);
    jumpIfTrue(mv, trueLabel);
    
    // FALSE: emit 0
    mv.iconst(0);
    mv.goTo(exitLabel);
    
    // TRUE: emit 1
    mv.mark(trueLabel);
    mv.iconst(1);
    
    mv.mark(exitLabel);
  }

  private void jumpIfTrue(MethodGenerator mv, Label trueLabel) {
    mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
  }
}
