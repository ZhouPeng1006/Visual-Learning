package entity;

import exception.*;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import utils.ErrorHandler;

import java.util.*;

public class ToplevelScope extends Scope {
    protected Map<String, Entity> entities;
    protected List<DefinedVariable> staticLocalVariables;   // cache

    public ToplevelScope() {
        super();
        entities = new LinkedHashMap<>();
        staticLocalVariables = null;
    }

    public boolean isToplevel() {
        return true;
    }

    public ToplevelScope toplevel() {
        return this;
    }


    /** Define variable or function globally. */
    public void defineEntity(Entity entity) throws SemanticException {
        Entity e = entities.get(entity.name());
        if (e != null && e.isDefined()) {
            throw new SemanticException("duplicated definition: " +
                    entity.name() + ": " +
                    e.location() + " and " + entity.location());
        }
        entities.put(entity.name(), entity);
    }

    /** Searches and gets entity searching scopes upto ToplevelScope. */
    public Entity get(String name) throws SemanticException {
        Entity ent = entities.get(name);
        if (ent == null) {
            throw new SemanticException("unresolved reference: " + name);
        }
        return ent;
    }

    public LLVMValueRef getAlloca(String name){
        return scopeAlloca.get(name);
    }

    public void putAlloca(String name, LLVMValueRef alloca){
        scopeAlloca.put(name, alloca);
    }

    public LLVMBasicBlockRef getScopeBasicBlock(String name) {
        return scopeBasicBlock.get(name);
    }

    public void putScopeBasicBlock(String name, LLVMBasicBlockRef block) {
        scopeBasicBlock.put(name, block);

    }

    public Integer getScopeParam(String name) {
        return scopeParams.get(name);
    }

    public void putScopeParam(String name, Integer var) {
        scopeParams.put(name,var);
    }

    /** Returns a list of all global variables.*/
    public List<Variable> allGlobalVariables() {
        List<Variable> result = new ArrayList<>();
        for (Entity ent : entities.values()) {
            if (ent instanceof Variable) {
                result.add((Variable)ent);
            }
        }
        result.addAll(staticLocalVariables());
        return result;
    }

    public List<DefinedVariable> definedGlobalScopeVariables() {
        List<DefinedVariable> result = new ArrayList<>();
        for (Entity ent : entities.values()) {
            if (ent instanceof DefinedVariable) {
                result.add((DefinedVariable)ent);
            }
        }
        result.addAll(staticLocalVariables());
        return result;
    }

    public List<DefinedVariable> staticLocalVariables() {
        if (staticLocalVariables == null) {
            staticLocalVariables = new ArrayList<>();
            for (LocalScope s : children) {
                staticLocalVariables.addAll(s.staticLocalVariables());
            }
            Map<String, Integer> seqTable = new HashMap<>();
            for (DefinedVariable var : staticLocalVariables) {
                Integer seq = seqTable.get(var.name());
                if (seq == null) {
                    var.setSequence(0);
                    seqTable.put(var.name(), 1);
                }
                else {
                    var.setSequence(seq);
                    seqTable.put(var.name(), seq + 1);
                }
            }
        }
        return staticLocalVariables;
    }

    public void checkReferences(ErrorHandler h) {
        for (Entity ent : entities.values()) {
            if (ent.isDefined()
                    && ent.isPrivate()
                    && !ent.isConstant()
                    && !ent.isRefered()) {
                h.warn(ent.location(), "unused variable: " + ent.name());
            }
        }
        // do not check parameters
        for (LocalScope funcScope : children) {
            for (LocalScope s : funcScope.children) {
                s.checkReferences(h);
            }
        }
    }
}
