package org.renjin.gcc.codegen.type.record;

import com.google.common.base.Preconditions;
import org.renjin.gcc.codegen.MethodGenerator;
import org.renjin.gcc.codegen.expr.Expressions;
import org.renjin.gcc.codegen.expr.GExpr;
import org.renjin.gcc.codegen.expr.JExpr;
import org.renjin.gcc.codegen.type.ReturnStrategy;
import org.renjin.gcc.codegen.type.TypeStrategy;
import org.renjin.repackaged.asm.Type;

/**
 * Strategy for returning record values represented by arrays.
 * 
 * <p>In C, returning a {@code struct} value, as opposed to </p>
 * 
 */
public class RecordArrayReturnStrategy implements ReturnStrategy {

  private RecordArrayValueFunction valueFunction;
  private Type arrayType;
  private int arrayLength;

  public RecordArrayReturnStrategy(RecordArrayValueFunction valueFunction, Type arrayType, int arrayLength) {
    this.valueFunction = valueFunction;
    Preconditions.checkArgument(arrayType.getSort() == Type.ARRAY, "Not an array type: " + arrayType);
    this.arrayType = arrayType;
    this.arrayLength = arrayLength;
  }

  @Override
  public Type getType() {
    return arrayType;
  }

  public Type getArrayComponentType() {
    // array type is [B for example,
    // so strip [
    String descriptor = arrayType.getDescriptor();
    return Type.getType(descriptor.substring(1));
  }

  /**
   * Returns an expression representing a copy of the given array value.
   *
   */
  @Override
  public JExpr marshall(GExpr value) {
    RecordArrayExpr arrayValue = (RecordArrayExpr) value;
    return arrayValue.copyArray();
  }

  @Override
  public GExpr unmarshall(MethodGenerator mv, JExpr returnValue, TypeStrategy lhsTypeStrategy) {
    return new RecordArrayExpr(valueFunction, returnValue, arrayLength);
  }

  @Override
  public JExpr getDefaultReturnValue() {
    return Expressions.newArray(getArrayComponentType(), arrayLength);
  }
}
