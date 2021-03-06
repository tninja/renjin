package org.renjin.gcc.codegen.array;

import org.renjin.gcc.codegen.MethodGenerator;
import org.renjin.gcc.codegen.expr.Expressions;
import org.renjin.gcc.codegen.expr.JExpr;
import org.renjin.gcc.codegen.expr.JLValue;
import org.renjin.gcc.codegen.fatptr.ValueFunction;
import org.renjin.gcc.codegen.fatptr.Wrappers;
import org.renjin.gcc.codegen.type.primitive.ConstantValue;
import org.renjin.gcc.codegen.var.LocalVarAllocator;
import org.renjin.repackaged.asm.Label;
import org.renjin.repackaged.asm.Type;

import javax.annotation.Nonnull;

/**
 * Allocates a new array and initializes each element with a loop.
 */
public class ArrayInitLoop implements JExpr {

  private ValueFunction valueFunction;
  private int arrayLength;
  private Type arrayType;

  public ArrayInitLoop(ValueFunction valueFunction, int arrayLength) {
    this.valueFunction = valueFunction;
    this.arrayLength = arrayLength;
    this.arrayType = Wrappers.valueArrayType(valueFunction.getValueType());
  }

  @Nonnull
  @Override
  public Type getType() {
    return arrayType;
  }

  @Override
  public void load(@Nonnull MethodGenerator mv) {
    
    JLValue array = mv.getLocalVarAllocator().reserve(arrayType);
    array.store(mv, Expressions.newArray(valueFunction.getValueType(), arrayLength));

    LocalVarAllocator.LocalVar counter = (LocalVarAllocator.LocalVar) mv.getLocalVarAllocator().reserveInt("$counter");
    Label loopHead = new Label();
    Label loopBody = new Label();
    
    // Initialize our loop counter
    counter.store(mv, new ConstantValue(Type.INT_TYPE, 0));
    mv.goTo(loopHead);
    
    // Loop body
    mv.visitLabel(loopBody);
    
    // Assign array element
    array.load(mv);
    counter.load(mv);
    valueFunction.getValueConstructor().get().load(mv);
    mv.astore(valueFunction.getValueType());
    
    mv.iinc(counter.getIndex(), 1);
    
    // Loop head
    mv.visitLabel(loopHead);
    counter.load(mv);
    mv.iconst(arrayLength);
    mv.ificmplt(loopBody);
    
    array.load(mv);
  }
}
