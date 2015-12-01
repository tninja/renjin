package org.renjin.gcc.codegen.call;

import com.google.common.collect.Lists;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.renjin.gcc.codegen.GeneratorFactory;
import org.renjin.gcc.codegen.expr.ExprGenerator;
import org.renjin.gcc.codegen.param.ParamGenerator;
import org.renjin.gcc.codegen.ret.ReturnGenerator;
import org.renjin.gcc.gimple.type.GimpleFunctionType;
import org.renjin.gcc.gimple.type.GimpleType;

import java.util.List;

/**
 * Emits the bytecode to invoke a function pointer (MethodHandle)
 */
public class FunPtrCallGenerator implements CallGenerator {

  private GeneratorFactory factory;
  private ExprGenerator funPtrGenerator;
  private final List<ParamGenerator> parameters;
  private final ReturnGenerator returnGenerator;
  private final GimpleFunctionType functionType;

  public FunPtrCallGenerator(GeneratorFactory factory, ExprGenerator funPtrGenerator) {
    this.factory = factory;
    this.funPtrGenerator = funPtrGenerator;
    functionType = funPtrGenerator.getGimpleType().getBaseType();
    parameters = factory.forParameterTypes(functionType.getArgumentTypes());
    returnGenerator = factory.findReturnGenerator(functionType.getReturnType());
  }

  /**
   * Write the bytecode for invoking a call to a function pointer, which we represent
   * as a MethodHandle in the compiled code. Since GCC permits casting function pointer 
   * signatures willy-nill, we need to infer the actual types from the call.
   *
   * @see <a href="http://stackoverflow.com/questions/559581/casting-a-function-pointer-to-another-type">Stack Overflow discussion</a>
   */
  @Override
  public void emitCall(MethodVisitor mv, List<ExprGenerator> argumentGenerators) {

    funPtrGenerator.emitPushMethodHandle(mv);
    
    // Infer the parameters types from the arguments provided
    List<Type> types = Lists.newArrayList();
    for (ExprGenerator argumentGenerator : argumentGenerators) {
      ParamGenerator paramGenerator = factory.forParameter(argumentGenerator.getGimpleType());
      paramGenerator.emitPushParameter(mv, argumentGenerator);
      types.addAll(paramGenerator.getParameterTypes());
    }
    
    // Use invoke() rather than invokeExact() to smooth over any type differences
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invoke", signature(types), false);
  }

  @Override
  public void emitCallAndPopResult(MethodVisitor mv, List<ExprGenerator> argumentGenerators) {
    emitCall(mv, argumentGenerators);
    switch (returnGenerator.getType().getSize()) {
      case 0:
        // NOOP;
        break;
      case 1:
        mv.visitInsn(Opcodes.POP);
        break;
      case 2:
        mv.visitInsn(Opcodes.POP2);
        break;
      default:
        throw new UnsupportedOperationException();
    }
  }

  private String signature(List<Type> types) {
    return Type.getMethodDescriptor(returnType(), types.toArray(new Type[types.size()]));
  }

  @Override
  public ExprGenerator expressionGenerator(GimpleType returnType, List<ExprGenerator> argumentGenerators) {
    return returnGenerator.callExpression(this, argumentGenerators);
  }
  
  private Type returnType() {
    return returnGenerator.getType();
  }

}
