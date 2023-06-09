package entity;

import exception.*;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import type.Type;
import utils.ErrorHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocalScope extends Scope {
    protected Scope parent;
    protected Map<String, DefinedVariable> variables;

    public LocalScope(Scope parent) {
        super();
        this.parent = parent;
        parent.addChild(this);
        variables = new LinkedHashMap<>();
    }

    public boolean isToplevel() {
        return false;
    }

    public ToplevelScope toplevel() {
        return parent.toplevel();
    }


    public boolean isDefinedLocally(String name) {
        return variables.containsKey(name);
    }

    /** Define variable in this scope. */
    public void defineVariable(DefinedVariable var) {
        if (variables.containsKey(var.name())) {
            throw new Error("duplicated variable: " + var.name());
        }
        variables.put(var.name(), var);
    }


    public Entity get(String name) throws SemanticException {
        DefinedVariable var = variables.get(name);
        if (var != null) {
            return var;
        }
        else {
            return parent.get(name);
        }
    }


    public LLVMValueRef getAlloca(String name){
        LLVMValueRef alloca = scopeAlloca.get(name);
        if (alloca != null) {
            return alloca;
        }
        else {
            return parent.getAlloca(name);
        }
    }

    public void putAlloca(String name, LLVMValueRef alloca){
        scopeAlloca.put(name, alloca);
    }

    public LLVMBasicBlockRef getScopeBasicBlock(String name) {
        LLVMBasicBlockRef block = scopeBasicBlock.get(name);
        if (block != null) {
            return block;
        }
        else {
            return parent.getScopeBasicBlock(name);
        }
    }

    public void putScopeBasicBlock(String name, LLVMBasicBlockRef block) {
        scopeBasicBlock.put(name, block);

    }

    public Integer getScopeParam(String name) {
        Integer ref = scopeParams.get(name);
        if (ref != null) {
            return ref;
        }
        else {
            throw new Error("Can not get Parameter: " + name);
        }
    }

    public void putScopeParam(String name, Integer ref) {
        scopeParams.put(name, ref);

    }

    /**
     * Returns all local variables in this scope.
     * The result DOES includes all nested local variables,
     * while it does NOT include static local variables.
     */
    public List<DefinedVariable> allLocalVariables() {
        List<DefinedVariable> result = new ArrayList<DefinedVariable>();
        for (LocalScope s : allLocalScopes()) {
            result.addAll(s.localVariables());
        }
        return result;
    }

    /**
     * Returns local variables defined in this scope.
     * Does NOT includes children's local variables.
     * Does NOT include static local variables.
     */
    public List<DefinedVariable> localVariables() {
        List<DefinedVariable> result = new ArrayList<DefinedVariable>();
        for (DefinedVariable var : variables.values()) {
            if (!var.isPrivate()) {
                result.add(var);
            }
        }
        return result;
    }

    /**
     * Returns all static local variables defined in this scope.
     */
    public List<DefinedVariable> staticLocalVariables() {
        List<DefinedVariable> result = new ArrayList<DefinedVariable>();
        for (LocalScope s : allLocalScopes()) {
            for (DefinedVariable var : s.variables.values()) {
                if (var.isPrivate()) {
                    result.add(var);
                }
            }
        }
        return result;
    }

    // Returns a list of all child scopes including this scope.
    protected List<LocalScope> allLocalScopes() {
        List<LocalScope> result = new ArrayList<LocalScope>();
        collectScope(result);
        return result;
    }

    protected void collectScope(List<LocalScope> buf) {
        buf.add(this);
        for (LocalScope s : children) {
            s.collectScope(buf);
        }
    }

    public void checkReferences(ErrorHandler h) {
        for (DefinedVariable var : variables.values()) {
            if (!var.isRefered()) {
                h.warn(var.location(), "unused variable: " + var.name());
            }
        }
        for (LocalScope c : children) {
            c.checkReferences(h);
        }
    }
}
