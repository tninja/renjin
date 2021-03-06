package org.renjin.gcc.codegen.type;

import org.renjin.gcc.codegen.MethodGenerator;
import org.renjin.gcc.codegen.expr.GExpr;
import org.renjin.gcc.codegen.expr.JExpr;
import org.renjin.repackaged.asm.Type;

/**
 * Provides a strategy for return values from methods.
 * 
 * <p>Because the JVM will only let us return a single value from a method,
 * we have to be sometimes creative in returning things like fat pointers, which
 * we represent using an array <i>and</i> and integer offset.</p>
 * 
 * @see org.renjin.gcc.codegen.fatptr.FatPtrReturnStrategy
 * @see org.renjin.gcc.codegen.type.complex.ComplexReturnStrategy
 */
public interface ReturnStrategy {

  /**
   * 
   * @return the JVM return type
   */
  Type getType();


  /**
   * Converts if necessary the expression to be returned to a single value.
   */
  JExpr marshall(GExpr expr);


  /**
   * Converts a function call return value to an expression if necessary.
   */
  GExpr unmarshall(MethodGenerator mv, JExpr returnValue, TypeStrategy lhsTypeStrategy);

  /**
   * Sometimes C code doesn't return a value despite having a non-void return type. In this case, 
   * we just need to push SOMETHING onto the stack.
   */
  JExpr getDefaultReturnValue();

}
