package entity;

import exception.*;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract public class Scope {
    protected List<LocalScope> children;
    protected Map<String, LLVMValueRef> scopeAlloca = new HashMap<>();
    protected Map<String, LLVMBasicBlockRef> scopeBasicBlock = new HashMap<>();
    protected Map<String, Integer> scopeParams = new HashMap<>();

    public Scope() {
        children = new ArrayList<>();
    }

    abstract public boolean isToplevel();
    abstract public ToplevelScope toplevel();

    protected void addChild(LocalScope s) {
        children.add(s);
    }

    abstract public Entity get(String name) throws SemanticException;

    abstract public LLVMValueRef getAlloca(String name);
    abstract public void putAlloca(String name, LLVMValueRef var);

    abstract public LLVMBasicBlockRef getScopeBasicBlock(String name);
    abstract public void putScopeBasicBlock(String name, LLVMBasicBlockRef var);

    abstract public Integer getScopeParam(String name);
    abstract public void putScopeParam(String name, Integer var);
}
