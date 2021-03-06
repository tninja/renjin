package org.renjin.compiler.codegen;

import org.renjin.compiler.TypeSolver;
import org.renjin.compiler.ir.ValueBounds;
import org.renjin.compiler.ir.tac.expressions.LValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps variables to their slot indexes o
 */
public class VariableSlots {
  
  private final Map<LValue, VariableStorage> storage = new HashMap<>();
  private final int firstSlot;
  private int nextSlot = 0;
  
  public VariableSlots(int parameterSize, TypeSolver types) {

    firstSlot = parameterSize;
    
    for (Map.Entry<LValue, ValueBounds> entry : types.getVariables().entrySet()) {
      LValue variable = entry.getKey();
      ValueBounds bounds = entry.getValue();
      if(bounds == null) {
        throw new NullPointerException("bounds(" + entry.getKey() + ")");
      }
      storage.put(variable, new VariableStorage(firstSlot + nextSlot, bounds.storageType()));
      
      nextSlot += variable.getType().getSize();
    }
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    for (Map.Entry<LValue, VariableStorage> entry : this.storage.entrySet()) {
      s.append(entry.getKey()).append(" => ").append(entry.getValue()).append("\n");
    }
    return s.toString();
  }

  public int getNumLocals() {
    return nextSlot;
  }

  public int getSlot(LValue lValue) {
    return storage.get(lValue).getSlotIndex();
  }

  public VariableStorage getStorage(LValue lhs) {
    return storage.get(lhs);
  }
  
}
